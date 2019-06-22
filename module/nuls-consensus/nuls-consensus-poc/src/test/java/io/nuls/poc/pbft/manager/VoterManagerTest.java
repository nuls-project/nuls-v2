package io.nuls.poc.pbft.manager;

import io.nuls.poc.pbft.BlockVoter;
import org.junit.Test;

/**
 * @author Niels
 */
public class VoterManagerTest {

    @Test
    public void start() {
    }

    /**
     * 1. 启动线程，开始计算投票数据
     *  1.1 当超时后，需要启动下一轮
     *  1.2 达成结果随时中止投票统计
     *  1.3 能得到全部共识节点信息
     * 2. 可以接受外部数据
     *  2.1 自己的签名
     *  2.2 收集到的签名
     * 3. 可以影响轮次的时间
     *  3.1 超时时可以延后打包时间
     * 4. 收集红黄牌数据
     * 5. 签名通过后通知区块模块进行处理
     *
     */





    @Test
    public void testStartThread(){
        BlockVoter voter = VoterManager.getVoter(2);
        try {
            Thread.sleep(100000L);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}