package server;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

public class TelnetServer {
    public static final String LS_COMMAND = "\tls view all files and directories\n";
    public static final String TOUCH_COMMAND = "\ttouch create file\n";
    public static final String MKDIR_COMMAND = "\tls create directory\n";
    public static final String CD_COMMAND = "\tls go to directory\n";
    public static final String RM_COMMAND = "\tls delete file\n";
    public static final String COPY_COMMAND = "\tls copy file\n";
    public static final String CAT_COMMAND = "\tls read file\n";
    public static final String NICKNAME_COMMAND = "\tnickname show your nickname\n";

    private ByteBuffer buffer = ByteBuffer.allocate(512);

    public TelnetServer() throws IOException {
        ServerSocketChannel channel = ServerSocketChannel.open();
        channel.bind(new InetSocketAddress(5678));
        channel.configureBlocking(false);
        Selector selector = Selector.open();
        channel.register(selector, SelectionKey.OP_ACCEPT);
        System.out.println("Server started");
        while (channel.isOpen()) {
            selector.select();
            Set<SelectionKey> keys = selector.selectedKeys();
            Iterator<SelectionKey> iterator = keys.iterator();
            while (iterator.hasNext()) {
                SelectionKey key = iterator.next();
                if (key.isAcceptable()) {
                    handleAccept(key, selector);
                } else if (key.isReadable()) {
                    handleRead(key, selector);
                }
                iterator.remove();
            }
        }
    }

    private void handleRead(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        SocketAddress client = channel.getRemoteAddress();
        int readBytes = channel.read(buffer);
        if (readBytes < 0) {
            channel.close();
            return;
        } else if (readBytes == 0) {
            return;
        }
        buffer.flip();
        StringBuilder builder = new StringBuilder();
        while (buffer.hasRemaining()) {
            builder.append((char) buffer.get());
        }
        buffer.clear();
        if (key.isValid()) {
            String command = builder.toString().replace("\n", "").replace("\r", "");
            if ("--help".equals(command)) {
                sendMessage(LS_COMMAND, selector, client);
                sendMessage(TOUCH_COMMAND, selector, client);
                sendMessage(MKDIR_COMMAND, selector, client);
                sendMessage(CD_COMMAND, selector, client);
                sendMessage(RM_COMMAND, selector, client);
                sendMessage(COPY_COMMAND, selector, client);
                sendMessage(CAT_COMMAND, selector, client);
                sendMessage(NICKNAME_COMMAND, selector, client);
            } else if ("ls".equals(command)) {
                sendMessage(getFileList().concat("\n"), selector, client);
            } else if ("exit".equals(command)) {
                sendMessage("Client logged out IP: " + client + "\n", selector, client);
                channel.close();
                return;
            }
        }
    }

    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        System.out.println("Client connected: " + channel.getRemoteAddress());
        channel.register(selector, SelectionKey.OP_READ);
        channel.write(ByteBuffer.wrap("Hello user!\n".getBytes(StandardCharsets.UTF_8)));
        channel.write(ByteBuffer.wrap("Enter --help for help\n".getBytes(StandardCharsets.UTF_8)));
    }

    private void sendMessage(String message, Selector selector, SocketAddress client) throws IOException {
        for (SelectionKey key : selector.keys()) {
            if (key.isValid() && key.channel() instanceof SocketChannel) {
                if (((SocketChannel) key.channel()).getRemoteAddress().equals(client)) {
                    ((SocketChannel) key.channel()).write(ByteBuffer.wrap(message.getBytes(StandardCharsets.UTF_8)));
                }
            }
        }
    }

    public String getFileList() {
        return String.join(" ", Objects.requireNonNull(new File("server").list()));
    }

    public static void main(String[] args) {
        try {
            new TelnetServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
