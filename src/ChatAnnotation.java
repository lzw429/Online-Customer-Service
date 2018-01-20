import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.concurrent.PriorityBlockingQueue;

@ServerEndpoint(value = "/ChatSocket")
public class ChatAnnotation {
    private Session session; // 与客户端通信的session
    private String nickname;
    private int userType;
    private static final int client = 0; // 客户
    private static final int service = 1; // 客服
    private static ArrayList<Client> clients = new ArrayList<>(); // 在线客户
    private static Comparator<Service> serviceComparator = new Comparator<Service>() {
        @Override // 覆盖比较器
        public int compare(Service s1, Service s2) {
            return s1.getMyClients().size() - s2.getMyClients().size(); // 客户数少的客服优先
        }
    };
    private static PriorityBlockingQueue<Service> services = new PriorityBlockingQueue<Service>(5, serviceComparator); // <线程安全的优先级队列>在线客服
    private static Hashtable<String, ChatAnnotation> connections = new Hashtable<>();


    @OnOpen
    public void start(Session session) throws IOException {
        this.session = session;
        this.nickname = session.getId();
        // 把当前客户端对应的ChatAnnotation对象加入到集合中
        connections.put(nickname, this);
    }

    @OnMessage
    public void incoming(String Msg) throws IOException {
        Gson gson = new Gson();
        Message comingMsg = gson.fromJson(Msg, Message.class);
        int msgType = comingMsg.getType(); // 消息类型
        String content = comingMsg.getContent(); // 消息内容
        if (msgType == 0)// 上线消息
        {
            if (content.equals("0")) // 客户上线
            {
                Client newClient = new Client(nickname);
                clients.add(newClient); // 添加到客户列表
                Service yourService = services.peek();
                if (yourService == null)// 没有客服在线
                {

                } else // 获得一个客服
                {
                    newClient.setMyService(yourService);
                    Message clientLogin = new Message(, nickname);
                    // 向客户反馈客服信息
                    // 告知客服有新客户
                }
            } else if (content.equals("1")) // 客服上线
            {
                Service newService = new Service(nickname);
                services.add(newService);
            }
        } else if (msgType == 1) // 普通消息
        {
            if (clients.contains(nickname))// 发送方是客户
            {
                content = "客户 " + nickname + " ：" + content;
            } else if (services.contains(nickname))// 发送方是客服
            {
                content = "客服 " + nickname + " ：" + content;
            }
            unicast(content, nickname, comingMsg.getReceiver(), LocalDateTime.now(), 1);
        }

    }

    @OnClose
    public void end() {
        for (Client c : clients) {
            if (c.getNickname().equals(nickname)) {
                clients.remove(c);// 删除客户列表中的一项
                // 告知该客户，其客服已离线
                // 删除其客服的客户列表中的一项
                break;
            }

        }
        for (Service s : services)
            if (s.getNickname().equals(nickname)) {
                for (Client client : s.getMyClients()) {
                    client.setMyService(services.peek()); // 为该客服的客户重新分配客服
                    // 告知客户已更换客服
                }
                services.remove(s);     // 删除客服列表中的一项
                break;
            }
        connections.remove(nickname);  // 删除连接哈希表中的一项
    }


    @OnError
    public void onError(Throwable t) throws Throwable {
    }


    private static void unicast(String msg, String sender, String receiver, LocalDateTime sendTime, int type) {
        Message Msg = new Message(msg, sender, receiver, sendTime, type);
        Gson gson = new GsonBuilder().create();
        String msgToJson = gson.toJson(Msg);
        ChatAnnotation recvChat = connections.get(receiver);
        ChatAnnotation sendChat = connections.get(sender);
        try {
            synchronized (recvChat) {
                recvChat.session.getBasicRemote().sendText(msgToJson); // 向接收方发送消息
            }
            synchronized (sendChat) {
                sendChat.session.getBasicRemote().sendText(msgToJson); // 发送方也需要收到自己发出的消息
            }
        } catch (IOException e) {
            connections.remove(recvChat);
            try {
                recvChat.session.close();
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            // 向sender告知对方连接已中断
        }
    }
}