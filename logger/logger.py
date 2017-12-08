import csv, cv2, datetime, ip, numpy, os, qrcode, signal, sys, threading, zmq
from PIL import Image, ImageTk
from StampedImage import StampedImage

if (sys.version_info > (3, 0)):
    # Python 3
    from tkinter import Tk, Label, BOTH
else:
    # Python 2
    from Tkinter import Tk, Label, BOTH

# Android image formats
formats = {
    4: "RGB_565",
    0x32315659: "YV12",
    0x20203859: "Y8",
    0x20363159: "Y16",
    0x10: "NV16",
    0x11: "NV21",
    0x14: "YUY2",
    0x100: "JPEG",
    0x23: "YUV_420_888",
    0x27: "YUV_422_888",
    0x28: "YUV_444_888",
    0x29: "FLEX_RGB_888",
    0x2A: "FLEX_RGBA_8888",
    0x20: "RAW_SENSOR",
    0x24: "RAW_PRIVATE",
    0x25: "RAW10",
    0x26: "RAW12",
}


def showQR(port):
    def center(toplevel):
        toplevel.update_idletasks()
        w = toplevel.winfo_screenwidth()
        h = toplevel.winfo_screenheight()
        size = tuple(int(_) for _ in toplevel.geometry().split('+')[0].split('x'))
        x = w / 2 - size[0] / 2
        y = h / 2 - size[1] / 2
        toplevel.geometry("%dx%d+%d+%d" % (size + (x, y)))

    addressAndPort = ip.get() + ":" + str(port)
    print(addressAndPort)
    root = Tk()
    root.overrideredirect(True)
    qr = qrcode.make(addressAndPort)
    img = Image.fromarray(255 * numpy.uint8(qr))
    imgTK = ImageTk.PhotoImage(img)
    panel = Label(root, image=imgTK)
    panel.pack(fill=BOTH, expand=True)
    center(root)
    root.update()


class BoundSubscriber():

    def __init__(self, hwm=20, subscriptionFilter=b""):

        # First, open the zmq socket
        self.context = zmq.Context()
        self.socket = self.context.socket(zmq.SUB)
        self.socket.set_hwm(hwm)
        address = 'tcp://*'
        self.port = self.socket.bind_to_random_port(address)
        addressAndPort = address + ":%s" % self.port
        print(addressAndPort)
        self.socket.setsockopt(zmq.SUBSCRIBE, subscriptionFilter)

        # Create the poison that will stop the looping
        self.poison = threading.Event()

        # The output directory
        self.dir = datetime.datetime.today().strftime("%y_%m_%d__%H_%M_%S")
        self.csv = None

    def loop(self):
        try:

            # Normally, a subscriber blocks. However, we want to be able to interrupt the
            # subscriber so that we can exit even when we haven't received a frame.
            # So we set up a poller with a timeout to do that for us
            # because you can't set a timeout on a subscriber directly.
            poller = zmq.Poller()
            poller.register(self.socket, zmq.POLLIN)

            # Create the new directory that we will save images in
            os.mkdir(self.dir)

            # Loop over frames until we are instructed to stop
            self.csv = []
            count = 0
            while not self.poison.is_set():

                # Poll the subscriber with a 1-second timeout.
                socks = dict(poller.poll(1000))
                if self.socket in socks and socks[self.socket] == zmq.POLLIN:

                    count += 1
                    data = self.socket.recv()
                    stampedImage = StampedImage.GetRootAsStampedImage(data, 0)

                    format = stampedImage.Image().Format()
                    r = stampedImage.Pose().RAsNumpy()
                    q = stampedImage.Pose().QAsNumpy()

                    # Now convert the image to rgb and save it
                    if formats[format] == "NV21":
                        width = stampedImage.Image().Width()
                        imageBytes = stampedImage.Image().DataAsNumpy()
                        imageBytes = imageBytes.reshape(len(imageBytes) // width, width)
                        rgb = cv2.cvtColor(imageBytes, cv2.COLOR_YUV2BGR_NV21)
                        filename = str(count) + ".png"
                        cv2.imwrite(os.path.join(self.dir, filename), rgb)
                        row = [filename]
                        row.extend(list(r))
                        row.extend(list(q))
                        self.csv.append(row)
                        print(row)

                else:
                    print("Looping, but nothing received.")
        except Exception as e:
            print(e)
            print("Shutting down subscriber")
            self.close()

    def close(self):
        self.poison.set()
        self.socket.close()
        if self.csv is not None:
            with open(os.path.join(self.dir, "data.csv"), "w", newline='') as f:
                writer = csv.writer(f)
                writer.writerows(self.csv)


sub = BoundSubscriber()
signal.signal(signal.SIGINT, sub.close)
showQR(sub.port)
sub.loop()
