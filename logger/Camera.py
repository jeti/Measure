from PIL import Image
from PIL import ImageTk
from tkinter import ttk
from tkinter.font import Font, nametofont
import cv2
import math 
import numpy
import queue
import re
import threading
import tkinter
import traceback
import zmq

# IP addresses that the user has previously entered. 
# We save these so the user doesn't have to enter it every time. 
saved_ips = ['192.168.1.144', '127.0.0.1', '134.102.149.212', '192.168.1.117', '134.102.144.12', '134.102.146.111'];

# The names displayed in the GUI along with the id's used internally:
open_cameras    = set()
cameras         = { "Back Camera" : 0, "Selfie Camera" : 1 }

# The ports on which the source device publishes and subscribes. 
# So if we want to receive, we use the source publisher port, for example.
pub_ports       = [ 4646, 4648 ];
sub_ports       = [ 4647, 4649 ];
get_messages    = { 
b"getSupportedPreviewFormats"  : b"setPreviewFormat"  , 
b"getSupportedPreviewFpsRange" : b"setPreviewFpsRange", 
b"getSupportedPreviewSizes"    : b"setPreviewSize"     };

# Android image formats 
formats         = {
    4           : "RGB_565",
    0x32315659  : "YV12",
    0x20203859  : "Y8",
    0x20363159  : "Y16",
    0x10        : "NV16",
    0x11        : "NV21",
    0x14        : "YUY2",
    0x100       : "JPEG",
    0x23        : "YUV_420_888",
    0x27        : "YUV_422_888",
    0x28        : "YUV_444_888",
    0x29        : "FLEX_RGB_888",
    0x2A        : "FLEX_RGBA_8888",
    0x20        : "RAW_SENSOR",
    0x24        : "RAW_PRIVATE",
    0x25        : "RAW10",
    0x26        : "RAW12",
}

class Camera:
    def __init__( self, root, source_ip, camera_id ):
        
        self.angle      = 0;
        self.button_w   = 50;
        self.button_h   = 50;
        self.camera_id  = camera_id;
        self.frame      = None;
        self.frames     = {};
        self.image_h    = None;
        self.image_w    = None;
        self.source_ip  = source_ip;
        self.lock       = threading.Lock();
        self.menus      = {};
        self.options    = {};
        self.poison     = {};
        self.queue_head = queue.Queue();
        self.queue_body = queue.Queue();
        self.rgb        = None;
        self.rgb_rot    = None;
        self.root       = root;
        self.tkframe    = None;
        self.vars       = {};
        self.window_h   = None;
        self.window_w   = None;
        
        # Configure a toolbar 
        self.toolbar = tkinter.Frame( root );
        self.toolbar.pack( fill = tkinter.X, side = "top" );
        
        # Create the "rotate image" buttons
        self.ccw_rotate_image   = ImageTk.PhotoImage(Image.open("100/rotate_ccw100.png").resize((self.button_w,self.button_h)));  
        ccw_rotate_button       = tkinter.Button( self.toolbar, command=self.change_rotation_ccw, image=self.ccw_rotate_image ).pack( side = "left" )
        self.cw_rotate_image    = ImageTk.PhotoImage(Image.open("100/rotate_cw100.png" ).resize((self.button_w,self.button_h)));    
        cw_rotate_button        = tkinter.Button( self.toolbar, command=self.change_rotation_cw, image=self.cw_rotate_image ).pack( side = "left" )
        
        # For each one of the get_messages, we will create an option menu 
        # in the toolbar where the user can select which value they want. 
        # Here we create empty frames for each option menu.
        for message in sorted(get_messages):
            self.frames[message]    = tkinter.Frame( self.toolbar );
            self.frames[message].pack( side = "left" );
        
        # Create a single thread for sending messages and making sure they are received.
        message                 = "getset";
        self.poison[message]    = threading.Event();
        threading.Thread( target= self.getset, args=(self.poison[message],) ).start();
        
        # Start the thread that receives camera frames.
        message                 = "subscriber";
        self.poison[message]    = threading.Event();
        threading.Thread( target= self.subscriber, args=(self.poison[message],) ).start();

        # Set up a callback to shut everything down when the window is closed
        self.root.wm_title( source_ip );
        self.root.wm_protocol( "WM_DELETE_WINDOW", self.close );
    
    # Get messages have the behavior that we query the source until we receive an answer. 
    # Once we receive a get message response (same header, but with a filled body), 
    # we stop sending the get message.    
    # Set messages have the behavior that we should always listen for changes in them 
    # since they are sent by the source when something changed. 
    # We can also send set messages to the source with a body when we want to change a parameter.
    # We should then receive the same message back from the source when the parameter changed. 
    # Regardless of whether we send a set message, we should always be listening for them 
    # because another subscriber may have changed one of the parameters.
    def getset( self, poison, hwm = 10, timeout_millis = 1000 ):
        try:
            
            # Create a set of all of the subscriptions, which include the get message keys and values.
            subscriptions   = set( get_messages ).union( get_messages.values() )
            
            # Open the ZeroMQ publisher and subscriber sockets.
            c   = zmq.Context();
            sub = c.socket( zmq.SUB );
            sub.set_hwm( hwm );
            sub.connect( "tcp://" + self.source_ip + ":" + str(pub_ports[self.camera_id]) );
            for subscription in subscriptions:
                sub.setsockopt( zmq.SUBSCRIBE, subscription );
            pub = c.socket( zmq.PUB );
            pub.set_hwm( hwm );
            pub.connect( "tcp://" + self.source_ip + ":" + str(sub_ports[self.camera_id]) );
            
            # A set of outstanding get message requests
            get_rqsts   = set( get_messages );
                                
            # Create a dictionary of messages that we are trying to send.
            # This dictionary will use the header as key and body as value.
            messages    = {};
            
            # Create a poller which will check every "timeout_millis" 
            # whether the subscriber received anything. 
            # If it didn't, then the publisher we will resend the message. 
            # If it did receive something, then we will run the callback.
            poller      = zmq.Poller();
            poller.register( sub, zmq.POLLIN ); 
            while not poison.is_set():
                
                # Poll for timeout_millis to see if there are any new messages
                socks   = dict(poller.poll(timeout_millis))
                if sub in socks and socks[sub] == zmq.POLLIN:
                    
                    # Get the message
                    head, body  = sub.recv_multipart();
                    
                    # Remove from the list of outstanding get requests and unsubscribe
                    if head in get_rqsts:
                        get_rqsts.discard( head );
                        sub.setsockopt( zmq.UNSUBSCRIBE, head );
                        self.modify_menu( head, body );
                    elif (head in messages) and (messages[head] == body):
                        # If this message was the one we requested, remove it from the query list.
                        messages.pop(head);
                    print("head: " + str(head) + ", body: " + str(body));
                    # TODO : Handle set messages
                    
                # See if there is anything new in the queue 
                try:
                    while not self.queue_head.empty():
                        
                        # Get the queued message header and body 
                        head    = self.queue_head.get(block=False); 
                        self.queue_head.task_done();
                        body    = self.queue_body.get(block=False); 
                        self.queue_body.task_done();
                        
                        # If the message was NOT in the subscription list, 
                        # subscribe and add it to the list
                        if head not in subscriptions:
                            sub.setsockopt( zmq.SUBSCRIBE, head );
                        
                        #Put the message in the dict, replacing the body if it exists
                        messages[head]  = body;
                except queue.Empty:
                    pass;
                
                # Resend all of the messages that the source didn't seem to receive yet.
                for head, body in messages.items():
                    pub.send( head, zmq.SNDMORE );
                    pub.send( body );

                # Resend the get requests 
                for head in get_rqsts:
                    pub.send( head );

        except:
            # Close the entire program from this thread if there was an exception.
            print("Exception on the getset thread.");
            traceback.print_exc();
            self.close();        
        finally:
            print("Shutting down the getset thread.");
            sub.close();
            pub.close();
    
    def modify_menu( self, head, body ):
        with self.lock:
            # If we haven't created this menu yet, do it now. 
            # TODO: There might be cases where we want to allow the menu to be 
            # updated, but at the moment, there are no such cases.
            if head not in self.menus:
                self.vars[head]     = tkinter.StringVar();
                self.options[head]  = self.string_to_list( str( body, encoding="UTF-8") ); 
                callback            = lambda event: self.set( head, event );
                self.menus[head]    = tkinter.OptionMenu( self.frames[head], self.vars[head], *self.options[head], command = callback )
                self.menus[head].pack();
    
    def string_to_list( self, str ):
        print( str )
        mod = re.sub( r'(.*)\]', r'\1', str ).replace("[","",1).split("][");
        print( mod )
        return mod;
    
    def set( self, head, event ):
        body = bytes( self.vars[head].get(), encoding="UTF-8" );
        print( "Setting: " + str(head) + " = " + str(body) );
        self.queue_head.put(get_messages[head]);
        self.queue_body.put(body);
    
    def subscriber( self, poison ):
        try:
        
            # Open the ZeroMQ subscriber socket
            c       = zmq.Context();
            sub     = c.socket(zmq.SUB);
            sub.connect("tcp://" + self.source_ip + ":" + str(pub_ports[self.camera_id]) );
            sub.setsockopt( zmq.SUBSCRIBE, b"Camera" );
            
            # Set a low highwater mark for the images. It makes no sense for us to have a big backlog.
            sub.set_hwm(1);
            
            # Normally, a subscriber blocks. However, we want to be able to interrupt the 
            # subscriber so that we can exit even when we haven't received a frame. 
            # So we set up a poller with a timeout to do that for us 
            # bceause you can't set a timeout on a subscriber directly.
            poller  = zmq.Poller();
            poller.register( sub, zmq.POLLIN ); 
            
            # Loop over frames until we are instructed to stop
            channels    = 3;
            while not poison.is_set():
                
                # Poll the subscriber with a 1-second timeout.
                socks = dict(poller.poll(1000))
                if sub in socks and socks[sub] == zmq.POLLIN:
                    
                    # Wait for the next message
                    header, data = sub.recv_multipart()
                    
                    # Parse the header information 
                    parsedHeader = header.split(b"x");
                    image_w = int( parsedHeader[1] );
                    image_h = int( parsedHeader[2] );
                    format  = int( parsedHeader[3] );
                    focalLength = float( parsedHeader[4] );
                    print( "frame width: " + str(image_w) + ", height: " + str(image_h) + ", format: " + 
                    str(format) + ", focal length: " + str(focalLength) + ", buffer length: " + str(len(data)) )
                    
                    # If the buffer is 0 (which seems to happen sometimes, we skip to the next frame)
                    if (len(data) == 0):
                        print( "Empty buffer. Skipping to next frame." );
                        continue;
                    
                    # If the frame size or format changed, we need to reallocate space for the rgb image 
                    if (image_w != self.image_w) or (image_h != self.image_h):
                        self.rgb        = numpy.zeros((image_h, image_w, channels), dtype=numpy.uint8);
                        self.image_w    = image_w;
                        self.image_h    = image_h;
                        print("Frame resized to " + str(image_w) + "x" + str(image_h) );

                    # Create an ndarray from the raw data. Be careful. This 
                    bytes   = numpy.ndarray( (len(data) // self.image_w, self.image_w), dtype = numpy.uint8, buffer = data );
                                        
                    # Convert the image to rgb
                    if format == 17:
                        cv2.cvtColor( bytes, cv2.COLOR_YUV2RGB_NV21, self.rgb, channels );
                    elif format == 842094169:
                        cv2.cvtColor( bytes, cv2.COLOR_YUV2RGB_YV12, self.rgb, channels );
                    
                    # Rotate the image 
                    self.rotate();
                    # Convert the openCV frame to a tkinter one
                    self.tkframe = Image.fromarray(self.rgb_rot);
                    
                    # if the frame is not None, we need to initialize it
                    if self.frame is None:
                        self.window_w   = self.image_w;
                        self.window_h   = self.image_h;
                        image           = ImageTk.PhotoImage(self.tkframe);
                        self.frame      = tkinter.Label(image=image);
                        self.frame.image = image;
                        self.frame.pack(side="left", fill=tkinter.BOTH, expand=1, padx=10, pady=10 );
                        self.frame.bind("<Configure>", self.resize_image);
                    # otherwise, simply update the frame
                    else:
                        self.fit_to_window();
                else:
                    print("Looping, but nothing received.");
        except:
            print( "Exception thrown on " + str(message) + " thread." );
            traceback.print_exc();
            sub.close();
            self.close();

    def fit_to_window(self):
        # Stretch a Image.fromarray image to fill the window while maintaining the aspect ratio
        ww  = self.tkframe.width;
        hh  = self.tkframe.height;
        rw  = 0.95 * self.window_w / ww;
        rh  = 0.95 * self.window_h / hh;
        if rw < rh:
            w = math.floor( rw * ww );
            h = math.floor( rw * hh );
        else: 
            w = math.floor( rh * ww );
            h = math.floor( rh * hh );
        image = ImageTk.PhotoImage(self.tkframe.resize((w,h)));
        self.frame.configure(image = image);
        self.frame.image = image;
    
    def resize_image(self,event):
        self.window_w = event.width;
        self.window_h = event.height;
        self.fit_to_window();

    def change_rotation_ccw(self):
        self.angle = (self.angle - 90) % 360;
        self.rotate();

    def change_rotation_cw(self):
        self.angle = (self.angle + 90) % 360;
        self.rotate();
        
    def rotate(self):
        if self.rgb is not None:
            if self.angle != 0:
                
                # Calculate the center of the rgb image. 
                # We will rotate about this point.
                ( x,y ) = (self.image_w // 2, self.image_h // 2);
                
                # grab the rotation matrix (applying the negative of the
                # angle to rotate clockwise), then grab the sine and cosine
                # (i.e., the rotation components of the matrix)
                R       = cv2.getRotationMatrix2D((x, y), -self.angle, 1.0);
                cos     = numpy.abs(R[0, 0]);
                sin     = numpy.abs(R[0, 1]);
                
                # Compute the bounded dimensions of the new image
                w       = int((self.image_w * cos) + (self.image_h * sin));
                h       = int((self.image_w * sin) + (self.image_h * cos));
                
                # adjust the rotation matrix to take into account translation
                R[0, 2] += (w / 2) - x;
                R[1, 2] += (h / 2) - y;
                
                # Rotate!
                self.rgb_rot = cv2.warpAffine( self.rgb, R, (w,h) );
            else: 
                self.rgb_rot = self.rgb;
        
    def close(self):
        for key,poison in self.poison.items():
            poison.set()
        self.root.quit()

# Set up the configuration pane
class Config:
    def __init__( self, root ):

        # Put everything in a frame that we will remove when the settings are confirmed
        self.root   = root;
        self.frame  = tkinter.Frame( root );
        self.frame.pack();
        
        # Create a Combobox for the source_ip address 
        self.source_ip  = tkinter.StringVar()
        self.source_ip.set( saved_ips[0] )
        ipbox           = ttk.Combobox( self.frame, textvariable=self.source_ip, values=saved_ips );
        ipbox.configure( justify="center" );
        ipbox.pack();
        # pprint(ipbox.config())
        
        # Create a dropdown of the camera selection
        self.camera_id  = tkinter.StringVar()
        values          = sorted( list(cameras.keys()), reverse=True );
        self.camera_id.set(values[0]);
        camera_menu     = ttk.Combobox( self.frame, textvariable=self.camera_id, values=values, state="readonly" );
        camera_menu.pack();
        
        # Create a connection button to verify the settings
        connect_button  = tkinter.Button( self.frame, text="Connect", command=self.connect ).pack()
        
    def connect(self):
        # Trim the source_ip address and see if it was already in the list of saved ips.
        # If not, we will modify this file so that next time, it is the first item in the list. 
        source_ip   = self.source_ip.get().strip();
        if source_ip in saved_ips:
            saved_ips.remove(source_ip)
        saved_ips.insert(0,source_ip);
            
        # Now rewrite the file 
        f       = open("Camera.py", "r+")
        s       = re.sub( r"(saved_ips\s+=[^\]]+\])", r'saved_ips = ' + str(saved_ips), f.read(), 1 );
        f.seek(0)
        f.write(s)
        f.close()
            
        # We need to keep track of the open cameras so that we can exit 
        # the tkinter mainloop externally when Ctrl+C is pressed. 
        open_cameras.add( Camera( self.root, self.source_ip.get(), cameras[self.camera_id.get()] ) );
        self.frame.destroy();

# Start the tkinter instance
root = tkinter.Tk()

# Create the font
font = "Ubuntu Mono";
size = 20;
nametofont("TkDefaultFont").configure(family=font, size=size )
nametofont("TkFixedFont").configure(family=font, size=size)
nametofont("TkTextFont").configure(family=font, size=size)

# Get the configuration settings
config = Config(root);
try:
    root.mainloop()
except:
    for camera in open_cameras:
        camera.close();a.close();