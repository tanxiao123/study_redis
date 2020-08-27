package web1;

import com.sun.org.apache.xerces.internal.xs.StringList;
import org.junit.Test;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

public class Login {
    public Jedis jedis;

    // 测试Token
    public final static String TEST_TOKEN = "arvndskn4";

    // 测试用户
    public final static String TEST_USER = "1";

    // 测试商品
    public final static String TEST_ITEM = "1";

    public final static Boolean QUIT = false;

    public final static Integer LIMIT = 10000000;

    public Login() {
        this.jedis = new Jedis("127.0.0.1", 6379);
        System.out.println("连接成功");
        System.out.println("服务器正在运行"+this.jedis.ping());
    }

    public static void main(String[] args) {
        Login instance = new Login();
        // 检测是否登录
//        System.out.println(instance.check_token("test"));
        // 用户token修改
//        System.out.println(instance.updateToken(Login.TEST_TOKEN, Login.TEST_USER));
        // 记录用户浏览商品记录
        System.out.println(instance.saveBrowse(Login.TEST_TOKEN,Login.TEST_ITEM));
    }

    /**
     * 检测用户是否已登录
     * @param token
     * @return
     */
    public String checkToken(String token) {
        return this.jedis.hget("login:", token);
    }

    public Boolean updateToken(String token, String user) {
        long timestamp = System.currentTimeMillis();
        this.jedis.hset("login:", token, user);
        // 维护令牌与已登陆用户之间的映射
        this.jedis.zadd("recent", timestamp, token);
        return true;
    }

    public Boolean saveBrowse(String token, String item) {
        long timestamp = System.currentTimeMillis();
        this.jedis.zadd("viewed:"+token, timestamp, item);
        // 移除旧的记录 只保留用户最近浏览过的25个商品
        this.jedis.zremrangeByRank("viewed"+token, 0,-26);
        return true;
    }

    /**
     * 清除过期令牌
     */
    public void clean_session() {
        Thread thread = new Thread() {
            @Override
            public void run() {
                while (Login.QUIT){
                    System.out.println("开始检测线程");
                    try {
                        long size = Login.this.jedis.zcard("recent:");
                        if (size <= Login.LIMIT){
                            Thread.sleep(1);
                            continue;
                        }
                        long end_index = Math.min(size - Login.LIMIT, 100);
                        Set<String> result = Login.this.jedis.zrange("rencent", 0, end_index - 1);
                        System.out.println("拿到的rencent数据信息:"+result.toString() );
                        List<String> list = new ArrayList<String>(result);
                        String[] session_keys = (String[]) list.toArray();
                        System.out.println("拿到的session_keys数据信息:"+ Arrays.toString(session_keys));
                        // 移除最旧的令牌
                        Login.this.jedis.del(session_keys);
                        Login.this.jedis.hdel("login:", session_keys);
                        Login.this.jedis.zrem("recent:", session_keys);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        };
    }

}
