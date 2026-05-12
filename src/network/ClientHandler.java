package network;

import org.example.util.JsonUtil;

import java.io.*;
import java.net.Socket;
import java.util.Map;

public class ClientHandler implements Runnable {

    private final Socket socket;
    private PrintWriter out;
    private String username = "unknown";

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        try (
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(
                        new OutputStreamWriter(socket.getOutputStream()), true)
        ) {
            this.out = writer;

            sendJson(Map.of(
                    "status",  "OK",
                    "message", "Connected to Auction Server"
            ));

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                System.out.println("[SERVER RECEIVED] " + inputLine);
                handleMessage(inputLine);
            }

        } catch (IOException e) {
            System.out.println("Client [" + username + "] disconnected.");
        } finally {
            AuctionServer.removeObserver(this);
            try { socket.close(); } catch (IOException e) { e.printStackTrace(); }
        }
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleMessage(String message) {
        if (message == null || message.isBlank()) return;

        try {
            Map<String, String> data = JsonUtil.parseFlat(message);
            String action = data.get("action");
            if (action == null) return;
            action = action.toUpperCase();

            switch (action) {

                case "LOGIN" -> {
                    username = data.getOrDefault("username", "unknown");
                    sendJson(Map.of(
                            "status",   "OK",
                            "message",  "Login success",
                            "username", username
                    ));
                    AuctionServer.broadcastAll(
                            JsonUtil.toJson(Map.of(
                                    "type",    "SYSTEM",
                                    "message", username + " đã tham gia."
                            ))
                    );
                    AuctionServer.broadcastOnlineCount();
                }

                case "PLACE_BID" -> {
                    String sessionId = data.getOrDefault("sessionId", "");
                    String amount    = data.getOrDefault("amount", "0");
                    AuctionServer.broadcastAll(
                            JsonUtil.toJson(Map.of(
                                    "type",      "NEW_BID",
                                    "username",  username,
                                    "sessionId", sessionId,
                                    "amount",    amount
                            ))
                    );
                    sendJson(Map.of("status", "OK", "message", "Bid placed successfully"));
                }

                case "CHAT" -> {
                    String chatMessage = data.getOrDefault("message", "");
                    // broadcast đến tất cả TRỪ người gửi
                    AuctionServer.broadcast(
                            JsonUtil.toJson(Map.of(
                                    "type",     "CHAT",
                                    "username", username,
                                    "message",  chatMessage
                            )), this
                    );
                }

                case "GET_ONLINE_COUNT" -> {
                    sendJson(Map.of(
                            "type",  "ONLINE_COUNT",
                            "count", String.valueOf(AuctionServer.getOnlineCount())
                    ));
                }

                default -> sendJson(Map.of("status", "ERROR", "message", "Unsupported action"));
            }

        } catch (Exception e) {
            sendJson(Map.of("status", "ERROR", "message", "Invalid format: " + e.getMessage()));
        }
    }

    private void sendJson(Map<String, String> data) {
        if (out != null) out.println(JsonUtil.toJson(data));
    }
}