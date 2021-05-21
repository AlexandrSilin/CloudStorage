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
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class TelnetServer {
    public static final String LS_COMMAND = "\tls view all files and directories\n";
    public static final String TOUCH_COMMAND = "\ttouch [filename] create file\n";
    public static final String MKDIR_COMMAND = "\tmkdir [dirname] create directory\n";
    public static final String CD_COMMAND = "\tcd go to directory\n";
    public static final String RM_COMMAND = "\trm [filename] delete file\n";
    public static final String COPY_COMMAND = "\tcopy [filename] copy file\n";
    public static final String CAT_COMMAND = "\tcat [filename] read file\n";
    public static final String NICKNAME_COMMAND = "\tnickname show your nickname\n";
    public static final String CHANGE_NICKNAME_COMMAND = "\tchangen [nick] show your nickname\n";

    private ByteBuffer buffer = ByteBuffer.allocate(512);
    private Path path = Path.of("server");
    private String nick;

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
            String[] command = builder.toString()
                    .replace("\n", "").replace("\r", "").replace("\\s++", " ")
                    .trim().split(" ", 2);
            switch (command[0]) {
                case "--help":
                    sendMessage(LS_COMMAND, selector, client);
                    sendMessage(TOUCH_COMMAND, selector, client);
                    sendMessage(MKDIR_COMMAND, selector, client);
                    sendMessage(CD_COMMAND, selector, client);
                    sendMessage(RM_COMMAND, selector, client);
                    sendMessage(COPY_COMMAND, selector, client);
                    sendMessage(CAT_COMMAND, selector, client);
                    sendMessage(NICKNAME_COMMAND, selector, client);
                    sendMessage(CHANGE_NICKNAME_COMMAND, selector, client);
                    break;
                case "ls":
                    sendMessage(getFileList().concat("\n"), selector, client);
                    break;
                case "touch":
                    if (command.length < 2) {
                        sendMessage("Bad command\n", selector, client);
                        break;
                    }
                    createFile(command[1], selector, client);
                    break;
                case "mkdir":
                    if (command.length < 2) {
                        sendMessage("Bad command\n", selector, client);
                        break;
                    }
                    createDirectory(command[1], selector, client);
                    break;
                case "cd":
                    if (command.length > 1) {
                        goToDirectory(command[1], selector, client);
                    }
                    break;
                case "rm":
                    if (command.length < 2) {
                        sendMessage("Bad command\n", selector, client);
                        break;
                    }
                    removeFile(command[1], selector, client);
                    break;
                case "copy":
                    if (command.length < 2) {
                        sendMessage("Bad command\n", selector, client);
                        break;
                    }
                    copyFile(command[1], selector, client);
                    break;
                case "cat":
                    if (command.length < 2) {
                        sendMessage("Bad command\n", selector, client);
                        break;
                    }
                    showFile(command[1], selector, client);
                    break;
                case "nickname":
                    sendMessage(nick + "\n", selector, client);
                    break;
                case "changen":
                    if (command.length < 2) {
                        sendMessage("Bad command\n", selector, client);
                        break;
                    }
                    nick = command[1];
                    sendMessage("Your nickname is " + nick + "\n", selector, client);
                    break;
                case "exit":
                    sendMessage("Client logged out IP: " + client + "\n", selector, client);
                    channel.close();
                default:
                    sendMessage("No such command\n", selector, client);
            }
        }
    }
    
    private void showFile(String filename, Selector selector, SocketAddress client) throws IOException {
        File file = new File(String.valueOf(path), filename);
        if (!file.exists()) {
            sendMessage("File doesn't exists\n", selector, client);
            return;
        }
        if (!Files.isReadable(file.toPath())) {
            sendMessage("Cant' read file\n", selector, client);
            return;
        }
        if (Files.isDirectory(file.toPath())) {
            sendMessage("Is a directory\n", selector, client);
            return;
        }
        sendMessage(Files.readString(file.toPath()) + "\n", selector, client);
    }

    private void copyFile(String command, Selector selector, SocketAddress client) throws IOException {
        String[] tmp = command.split(" ");
        String copyTo = tmp[1];
        File file = new File(String.valueOf(path), tmp[0]);
        if (!file.exists()) {
            sendMessage("File doesn't exists\n", selector, client);
            return;
        }
        String[] tmpPath = copyTo.split("/");
        Path dstPath = Path.of("server");
        Path srcPath = Path.of(String.valueOf(path), tmp[0]);
        for (String string : tmpPath){
            dstPath = Path.of(String.valueOf(dstPath), string);
        }
        if (!Files.isDirectory(dstPath)) {
            sendMessage("Directory doesn't exists\n", selector, client);
            return;
        }
        try {
            Files.copy(srcPath, dstPath.resolve(srcPath.getFileName()));
        } catch (FileAlreadyExistsException exception) {
            sendMessage("File already exists\n", selector, client);
        }
        sendMessage("Success\n", selector, client);
    }

    private void removeFile(String filename, Selector selector, SocketAddress client) throws IOException {
        File file = new File(String.valueOf(path), filename);
        if (Files.exists(file.toPath())) {
            Files.delete(file.toPath());
            sendMessage("Success\n", selector, client);
            return;
        }
        sendMessage("File doesn't exists\n", selector, client);
    }

    private void goToDirectory(String dirname, Selector selector, SocketAddress client) throws IOException {
        if ("..".equals(dirname)) {
            if (path.equals(Path.of("server"))) {
                return;
            }
            path = path.getParent();
            return;
        }
        Path tmp = Path.of(String.valueOf(path), dirname);
        if (!Files.isDirectory(tmp)) {
            sendMessage("Directory doesn't exists\n", selector, client);
            return;
        }
        path = tmp;
    }

    private void createFile(String filename, Selector selector, SocketAddress client) throws IOException {
        File file = new File(String.valueOf(path), filename);
        if (file.exists()) {
            sendMessage("File exists\n", selector, client);
            return;
        }
        Files.createFile(file.toPath());
        sendMessage("File created\n", selector, client);
    }

    private void createDirectory(String dirName, Selector selector, SocketAddress client) throws IOException {
        if (!(dirName.trim().length() > 0 && dirName.matches("[a-zA-Z]*\\d*"))) {
            sendMessage("Bad directory name\n", selector, client);
            return;
        }
        if (Files.isDirectory(Path.of(String.valueOf(path), dirName))) {
            sendMessage("Directory exists\n", selector, client);
            return;
        }
        Files.createDirectory(Path.of(String.valueOf(path), dirName));
        sendMessage("Success\n", selector, client);
    }

    private void handleAccept(SelectionKey key, Selector selector) throws IOException {
        SocketChannel channel = ((ServerSocketChannel) key.channel()).accept();
        channel.configureBlocking(false);
        System.out.println("Client connected: " + channel.getRemoteAddress());
        nick = String.valueOf(channel.getLocalAddress());
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
        return String.join(" ", Objects.requireNonNull(new File(String.valueOf(path)).list()));
    }

    public static void main(String[] args) {
        try {
            new TelnetServer();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
