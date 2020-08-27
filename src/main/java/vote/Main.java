package vote;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import redis.clients.jedis.Jedis;
import redis.clients.jedis.ZParams;

public class Main {
    public Jedis redis;

    public static final Integer ONE_WEEK_IN_SECONDS = 7 * 86400;

    public static final Integer VOTE_SCORE = 432;

    // 分页显示条数
    public static final Integer ARTICLES_PER_PAGE = 25;


    public static final String TESTUSER = "user:10000";

    public static final String TESTARTICLE = "article:4";

    public Main() {
        this.redis = new Jedis("127.0.0.1", 6379);
        System.out.println("连接成功");
        System.out.println("服务器正在运行"+this.redis.ping());
    }


    public static void main(String[] args) {
        Main instance  = new Main();
        // 发布文章
//		System.out.println(instance.post_article(Vote.TESTUSER, "测试文章2", "https://www.baidu.com"));
        // 进行投票
//		System.out.println(instance.article_vote(Vote.TESTUSER, Vote.TESTARTICLE));
        // 获取文章列表
//		System.out.println(instance.get_articles(1));
        // 文章进行分组
//		String[] to_add = {"娱乐","搞笑"};
//		instance.add_groups(Vote.TESTARTICLE, to_add);

    }

    /**
     * 投票操作
     * @param user 投票用户
     * @param article 文章
     * @return
     */
    public Boolean article_vote(String user, String article) {
        // 检测文件是否发布超过一周 超过后不允许进行投票操作
        int timestamp = (int) System.currentTimeMillis() - Main.ONE_WEEK_IN_SECONDS;
        System.out.println(this.redis);
        Double result = this.redis.zscore("time:", article);
        if (result == null || result < timestamp) {
            return false;
        }
        String article_id = article.split(":")[1];
        // 向已投票名单里新增数据 如果新增成功 那么用户是第一次投票 可以投票
        if (this.redis.sadd("vote:"+article_id, user) > 0) {
            // 向文章分数有序集合(Zset)内自增投票分数
            this.redis.zincrby("score", Main.VOTE_SCORE, article);
            // 初始化投票数量
            this.redis.hincrBy(article, "votes", 1);
        }
        return true;
    }

    /**
     * 创建文章操作
     * @param user 用户
     * @param title 标题
     * @param link
     * @return
     */
    public Boolean post_article(String user, String title, String link) {
        Long article_id = this.redis.incr("article:");
        System.out.println("生成的自增ID为"+article_id);
        String voted = "voted:"+article_id;
        // 将发布名单的用户添加到已投票名单
        this.redis.sadd(voted, user);
        // 设置已投票名单的有效期为一周
        this.redis.expire(voted, Main.ONE_WEEK_IN_SECONDS);
        String article = "article:"+article_id;
        String now = String.valueOf(System.currentTimeMillis());
        Map<String, String> map = new HashMap<String, String>();
        map.put("title", title);
        map.put("link", link);
        map.put("poster", user);
        map.put("time", now);
        map.put("votes", "1");
        this.redis.hmset(article, map);
        // 创建初始化分数
        this.redis.zadd("score:", Double.valueOf(Double.valueOf(now)+Main.VOTE_SCORE), article);
        // 创建初始化时间
        this.redis.zadd("time:", Double.valueOf(now), article);
        return true;

    }

    /**
     * 获取文章列表接口
     * @param page
     * @return
     */
    public List<Map<String, String>> get_articles(Integer page, String key) {
        Integer start = (page -1) * Main.ARTICLES_PER_PAGE;
        Integer end = start + Main.ARTICLES_PER_PAGE - 1;
        if (key == null || "".equals(key)) {
            key = "score:";
        }
        Set<String> result = this.redis.zrevrange(key, start, end);
        List<Map<String, String>> response = new ArrayList<Map<String,String>>();
        for (String id : result) {
            Map<String, String> article_data = this.redis.hgetAll(id);
            article_data.put("id", id);
            response.add(article_data);
        }
        return response;
    }


    /**
     * 添加文章分组
     * @param article_id
     * @param to_add
     * @return
     */
    public Boolean add_groups(String article_id, String[] to_add) {
        String article = "article:"+article_id;
        for (String group : to_add) {
            this.redis.sadd("group:"+group, article);
        }
        return true;
    }

    /**
     * 删除文章分组
     * @param article_id
     * @param to_remove
     * @return
     */
    public Boolean remove_groups(String article_id,String[] to_remove) {
        String article = "article:"+article_id;
        for (String group : to_remove) {
            this.redis.srem("group:"+group, article);
        }
        return true;
    }

    /**
     * 分组排序获取文章列表
     * @param group
     * @param page
     * @return
     */
    public List<Map<String, String>> get_group_articles(String group, Integer page) {
        String order = "score:";
        String key = order + group;
        if (!this.redis.exists(key)) {
            // 使用zinterstore 对redis zset 与 set 进行合并 合成一个新的zset元素 由于set没有分值 默认为1
            // 在此设置weights Aggregate为Max 则不求交集  求两个元素中的最大分值
            String[] zSetKeys = {"group:"+group, order};
            this.redis.zinterstore(key, new ZParams().aggregate(ZParams.Aggregate.MAX), zSetKeys);
        }
        // 设置这个临时key的过期时间
        this.redis.expire(key, 60);
        // 在此复用之前获取文章列表接口  默认key改为刚生成排序好的key
        return this.get_articles(page, key);
    }
}
