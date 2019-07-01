package io.nuls.poc.utils.manager;

import io.nuls.base.data.BlockExtendsData;
import io.nuls.base.data.BlockHeader;
import io.nuls.core.core.annotation.Autowired;
import io.nuls.core.core.annotation.Component;
import io.nuls.poc.model.bo.Chain;
import io.nuls.poc.model.bo.round.MeetingRound;

/**
 * 轮次信息管理类
 * Round Information Management Class
 *
 * @author tag
 * 2018/11/14
 */
@Component
public class RoundManager {

    @Autowired
    private PocRoundManager pocRoundManager;

    @Autowired
    private PbftRoundManager pbftRoundManager;

    /**
     * 添加轮次信息到轮次列表中
     * Add Round Information to Round List
     *
     * @param chain        chain info
     * @param meetingRound 需添加的轮次信息/round info
     */
    public void addRound(Chain chain, MeetingRound meetingRound) {
        getRoundManager(chain).addRound(chain, meetingRound);
    }

    private IRoundManager getRoundManager(Chain chain) {
        if (chain.getConfig().getPbft() == 1) {
            return this.pbftRoundManager;
        }
        return this.pocRoundManager;
    }

    /**
     * 回滚本地轮次到指定轮次
     *
     * @param roundIndex 回滚到指定轮次
     * @param chain      链信息
     */
    public void rollBackRound(Chain chain, long roundIndex) {
        this.getRoundManager(chain).rollBackRound(chain, roundIndex);
    }

    /**
     * 清理指定链的轮次信息
     * Clean up the wheel number information of the specified chain
     *
     * @param chain chain info
     * @param count 保留几轮轮次信息/Keep several rounds of information
     * @return boolean
     */
    public boolean clearRound(Chain chain, int count) {
        return this.getRoundManager(chain).clearRound(chain, count);
    }

    /**
     * 清理比指定轮次之后的轮次信息
     * Clean up the wheel number information of the specified chain
     *
     * @param chain      chain info
     * @param roundIndex 保留几轮轮次信息/Keep several rounds of information
     * @return boolean
     */
    public boolean clearRound(Chain chain, long roundIndex) {
        return this.getRoundManager(chain).clearRound(chain, roundIndex);
    }

    /**
     * 获取指定下标的轮次信息
     * Get round information for specified Subscripts
     *
     * @param chain      chain info
     * @param roundIndex 轮次下标/round index
     * @return MeetingRound
     */
    public MeetingRound getRoundByIndex(Chain chain, long roundIndex) {
        return this.getRoundManager(chain).getRoundByIndex(chain, roundIndex);
    }

    /**
     * 检查是否需要重置轮次
     * Check if you need to reset rounds
     *
     * @param chain chain info
     */
    public void checkIsNeedReset(Chain chain) throws Exception {
        this.getRoundManager(chain).checkIsNeedReset(chain);
    }

    /**
     * 获取本地最新轮次信息
     * Get the latest local rounds
     *
     * @param chain chain info
     * @return MeetingRound
     */
    public MeetingRound getCurrentRound(Chain chain) {
        return this.getRoundManager(chain).getCurrentRound(chain);
    }

    /**
     * 初始化轮次信息（重新计算轮次信息）
     * Initialize Round Information (recalculate Round Information)
     *
     * @param chain chain info
     */
    public void initRound(Chain chain) throws Exception {
        this.getRoundManager(chain).initRound(chain);
    }

    /**
     * 重设最新轮次信息
     * Reset the latest round information
     *
     * @param chain      chain info
     * @param isRealTime 是否根据最新时间计算轮次/Whether to calculate rounds based on current time
     * @return MeetingRound
     */
    public MeetingRound resetRound(Chain chain, boolean isRealTime) throws Exception {
        return getRoundManager(chain).resetRound(chain, isRealTime);
    }

    public MeetingRound createNextRound(Chain chain, BlockHeader bestBlockheader, MeetingRound round) throws Exception {
        return getRoundManager(chain).createNextRound(chain, bestBlockheader, round);
    }

    /**
     * 获取下一轮的轮次信息
     * Get the next round of round objects
     *
     * @param chain      chain info
     * @param roundData  轮次数据/block extends entity
     * @param isRealTime 是否根据最新时间计算轮次/Whether to calculate rounds based on current time
     * @return MeetingRound
     */
    public MeetingRound getRound(Chain chain, BlockHeader header, BlockExtendsData roundData, boolean isRealTime) throws Exception {
        return this.getRoundManager(chain).getRound(chain, header, roundData, isRealTime);
    }


    public MeetingRound getRoundByRoundIndex(Chain chain, long roundIndex, long roundStartTime, long offset, int currentMemberIndex) throws Exception {
        return this.getRoundManager(chain).getRoundByRoundIndex(chain, roundIndex, roundStartTime, offset, currentMemberIndex);
    }


    /**
     * 获取指定轮次前一轮打包的第一个区块
     * Gets the first block packaged in the previous round of the specified round
     *
     * @param chain      chain info
     * @param roundIndex 轮次下标
     */
    public BlockHeader getFirstBlockOfPreRound(Chain chain, long roundIndex) {
        return getRoundManager(chain).getFirstBlockOfPreRound(chain, roundIndex);
    }

}
