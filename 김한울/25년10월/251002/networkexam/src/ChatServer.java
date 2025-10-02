import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


public class ChatServer {
    private static final int PORT = 8888;
    private static Map<String, ClientHandler> clients = new ConcurrentHashMap<>();

    private static void broadcast(String message, ClientHandler sender) {
        for (ClientHandler client : clients.values()) {
            if (!client.equals(sender)) {
                client.sendMessage(sender.nickname + ":::" + message);
            } else {
                client.sendMessage("내가 보낸메시지 ::" + message);
            }
        }
    }

    private static void whispering(String message, ClientHandler sender, ClientHandler to) {
        if (!to.equals(sender)) {
            to.sendMessage("whisper -> " + sender.nickname + ":::" + message);
            sender.sendMessage("me -> ::" + message);
        } else {
            sender.sendMessage("[System]::" + "자신한테는 보낼 수 없습니다.");
        }
    }

    private static void sendHelpMsg(ClientHandler me) {
        String helpMsg = "********************************************" +
                "        1. 일반 메시지: 내용을 입력 후 엔터\n" +
                "        2. 귓속말: /to [대상닉네임] [내용] 형식으로 입력\n" +
                "           예) /to userA 안녕하세요.\n" +
                "        3. 종료: bye 를 입력 후 엔터\n" +
                "        4. 도움말: /help 를 입력 후 엔터" +
                "********************************************";
        me.sendMessage(helpMsg);
    }


    public static void main(String[] args) {
        System.out.println("채팅 서버 시작!!!");
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket socket = serverSocket.accept();
                ClientHandler clientHandler = new ClientHandler(socket);
                new Thread(clientHandler).start();
            }
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    static class ClientHandler implements Runnable {
        private Socket socket;
        private PrintWriter out;
        private String nickname;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        public void sendMessage(String message) {
            if (out != null) {
                out.println(message);
            }
        }

        public boolean isCmd(String msg) {

            if (msg.startsWith("/to")) {
                String[] parts = msg.split(" ", 3); // "/to userA 내용"
                if (parts.length < 3) {
                    this.sendMessage("[System] 귓속말 형식: /to [닉네임] [메시지]");
                    return true;
                }
                String toNickname = parts[1];
                String msgFromMe = parts[2];

                ClientHandler target = clients.get(toNickname);
                if (target != null) {
                    whispering(msgFromMe, this, target);
                } else {
                    this.sendMessage("[System]:::" + toNickname + " 님은 현재 접속 중이 아닙니다.");
                }
                return true;

            } else if (msg.startsWith("/help")) {
                sendHelpMsg(this);
                return true;
            }
            return false;
        }

        @Override
        public void run() {
            try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            ) {
                out = new PrintWriter(socket.getOutputStream(), true);

                //닉네임을 입력받고 싶다.
                out.println("닉네임을 입력하세요.");
                nickname = in.readLine();

                if (nickname == null) {
                    return;
                }
                //닉네임 중복 확인
                while (clients.containsKey(nickname)) {
                    this.sendMessage("[System]:: 중복된 닉네임이 있습니다. 다시 입력해주세요.");
                    nickname = in.readLine();
                }
                System.out.println(nickname + "님 입장");
                
                //인원 등록
                clients.put(nickname, this);

                //채팅방에 있는 전체 사용자에게 알리고 싶다.
                broadcast(nickname + " 님 입장", this);
                System.out.println("서버 접속 인원 수 : " + clients.size());

                String message = null;
                while ((message = in.readLine()) != null) {

                    if (isCmd(message)) continue;

                    //sendMsg
                    System.out.println(nickname + ":::" + message);
                    if ("bye".equalsIgnoreCase(message)) {
                        clients.remove(nickname);
                        System.out.println("서버 접속 인원 수 : " + clients.size());
                        return;
                    }
                    broadcast(message, this);
                }
            } catch (Exception e) {
                System.out.println(e.getMessage());
            } finally {
                if (nickname != null) {
                    System.out.println(nickname + "님 퇴장");
                    broadcast(nickname + "님 퇴장", this);
                }
                try {
                    socket.close();
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }

        }
    }

}
