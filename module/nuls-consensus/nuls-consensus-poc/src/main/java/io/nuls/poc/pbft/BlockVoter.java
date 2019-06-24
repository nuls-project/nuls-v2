package io.nuls.poc.pbft;

import io.nuls.base.basic.AddressTool;
import io.nuls.base.data.Block;
import io.nuls.base.data.BlockHeader;
import io.nuls.base.data.NulsHash;
import io.nuls.core.constant.ErrorCode;
import io.nuls.core.core.ioc.SpringLiteContext;
import io.nuls.core.crypto.Sha256Hash;
import io.nuls.core.exception.NulsException;
import io.nuls.core.model.ArraysTool;
import io.nuls.core.rpc.util.NulsDateUtils;
import io.nuls.poc.constant.ConsensusErrorCode;
import io.nuls.poc.constant.PocMessageType;
import io.nuls.poc.model.bo.Chain;
import io.nuls.poc.model.bo.round.MeetingMember;
import io.nuls.poc.model.bo.round.MeetingRound;
import io.nuls.poc.pbft.cache.PreCommitCache;
import io.nuls.poc.pbft.cache.VoteCenter;
import io.nuls.poc.pbft.constant.VoteConstant;
import io.nuls.poc.pbft.message.VoteMessage;
import io.nuls.poc.pbft.model.PbftData;
import io.nuls.poc.pbft.model.VoteResultItem;
import io.nuls.poc.pbft.model.VoteRound;
import io.nuls.poc.rpc.call.CallMethodUtils;
import io.nuls.poc.utils.LoggerUtil;
import io.nuls.poc.utils.manager.RoundManager;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * @author Niels
 */
public class BlockVoter implements Runnable {

    private long timeout;

    private final int chainId;
    private final Chain chain;
    private final RoundManager roundManager;
    private VoteCenter cache;
    private PreCommitCache preCommitCache = PreCommitCache.getInstance();
    private VoteRound lastRound;

    private VoteRound curRound;
    private BlockHeader lastHeader;
    private MeetingRound pocRound;

    public BlockVoter(Chain chain) {
        this.chainId = chain.getConfig().getChainId();
        this.chain = chain;
        this.timeout = chain.getConfig().getPackingInterval();
        this.roundManager = SpringLiteContext.getBean(RoundManager.class);
        this.cache = new VoteCenter(chainId);
        new Thread(this).start();
    }

    @Override
    public void run() {
        while (true) {
            try {
                if (null == this.roundManager) {
                    Thread.sleep(1000L);
                    continue;
                }
                long sleep = 200L;
                if (null == pocRound || pocRound.getMemberCount() == (1 + pocRound.getCurrentMemberIndex())) {
                    this.pocRound = this.roundManager.getCurrentRound(chain);
                }

                if (null != pocRound && pocRound.getMyMember() != null) {
                    doit(pocRound);
                } else {
                    sleep = 5000L;
                }
                Thread.sleep(sleep);
            } catch (Exception e) {
                LoggerUtil.commonLog.error(e);
                try {
                    Thread.sleep(1000L);
                } catch (InterruptedException ex) {
                    LoggerUtil.commonLog.error(e);
                }
            }
        }
    }

    private void doit(MeetingRound pocRound) {
        long now = NulsDateUtils.getCurrentTimeSeconds();
        this.lastHeader = chain.getNewestHeader();
        MeetingMember meetingMember = pocRound.getMember(pocRound.getCurrentMemberIndex());
        long offset = now - meetingMember.getStartTime() - pocRound.getOffset() - this.timeout;
        long round = offset / this.timeout + 1;
        if (this.curRound == null) {
            changeCurrentRound(round);
            return;
        }
        if (now < this.curRound.getStart()) {
            return;
        }
        PbftData pbftData = cache.getCurrentResult(this.curRound.getHeight(), this.curRound.getRound());
        if (null != pbftData) {
            VoteResultItem result = pbftData.getVote2LargestItem();
            if (result.getCount() > VoteConstant.DEFAULT_RATE * pocRound.getMemberCount()) {
                return;
            }
        }
        if (now < this.curRound.getEnd()) {
            return;
        }
        long start = this.curRound.getEnd();
        this.changeCurrentRound(round);
        if (preCommitCache.getForkHeader() != null) {
            LoggerUtil.commonLog.info("====投票给分叉");
            preCommitVote(this.curRound.getHeight(), (int) round, preCommitCache.getShouldNext(), preCommitCache.getHeader(), start, preCommitCache.getForkHeader(), pocRound.getMyMember());
        } else {
            pocRound.setOffset(pocRound.getOffset() + this.timeout);
            LoggerUtil.commonLog.info("====投票给空块：{}", this.curRound.getHeight());
            this.preCommitVote(this.curRound.getHeight(), (int) round, null, null, start, null, pocRound.getMyMember());
        }
    }

    private void changeCurrentRound(long round) {
        this.lastRound = this.curRound;
        this.curRound = new VoteRound();
        this.curRound.setHeight(this.lastHeader.getHeight() + 1);
        this.curRound.setRound((int) round);
        this.curRound.setStart(this.lastHeader.getTime() + round * this.timeout);
        this.curRound.setEnd(this.curRound.getStart() + this.timeout);
    }

    private void sureResult(long height, NulsHash hash, MeetingRound pocRound) {
        boolean result = CallMethodUtils.sendVerifyResult(chainId, height, hash);
        if (result) {
            this.preCommitCache.clear(hash);
            pocRound.setCurrentMemberIndex(pocRound.getCurrentMemberIndex() + 1);
        }
    }

    public ErrorCode recieveBlock(Block block) {
        ErrorCode code = ConsensusErrorCode.WAIT_BLOCK_VERIFY;
        long height = block.getHeader().getHeight();
        NulsHash hash = block.getHeader().getHash();
        PbftData pbftData = cache.addVote1(height, 1, hash, block.getHeader().getPackingAddress(chainId), block.getHeader().getTime());

        int totalCount = pocRound.getMemberCount();
        if (totalCount == 1) {
            code = ConsensusErrorCode.SUCCESS;
            return code;
        }
        //判断自己是否需要签名，如果需要就直接进行
        MeetingMember self = pocRound.getMyMember();
        if (null != self && !pbftData.hasVoted1(self.getAgent().getPackingAddress())) {
            long now = NulsDateUtils.getCurrentTimeSeconds();
            long offset = now - block.getHeader().getTime();
            long round = offset / this.timeout + 1;
            LoggerUtil.commonLog.info("===投票给一个区块：{}, {}", block.getHeader().getHeight(), block.getHeader().getHash());
            preCommitVote(height, (int) round, block.getHeader().getHash(), block.getHeader(), block.getHeader().getTime() + this.timeout * (round - 1), null, self);
        }
        VoteResultItem result = pbftData.getVote1LargestItem();
        if (result.getCount() > VoteConstant.DEFAULT_RATE * totalCount && !pbftData.hasVoted2(self.getAgent().getPackingAddress())) {
            VoteMessage message = new VoteMessage();
            message.setHeight(height);
            message.setRound(pbftData.getRound());
            message.setStep((byte) 1);
            message.setStartTime(block.getHeader().getTime());
            message.setHash(result.getHash());
            this.signAndBroadcast(self, message);
            LoggerUtil.commonLog.info("====commit轮投票：{} ,{}", height, hash);
            cache.addVote2(height, pbftData.getRound(), hash, self.getAgent().getPackingAddress(), block.getHeader().getTime());
        }
        return code;
    }

    private void preCommitVote(long height, int round, NulsHash hash, BlockHeader header, long time, BlockHeader forkHeader, MeetingMember self) {
        VoteMessage message = new VoteMessage();
        message.setHeight(height);
        message.setRound(round);
        message.setStep((byte) 0);
        message.setStartTime(time);
        message.setHash(hash);
        if (null != header) {
            message.setHeader1(header);
            message.setHeader2(forkHeader);
        }
        this.signAndBroadcast(self, message);
        this.preCommitCache.change(height, round, preCommitCache.getShouldNext(), header, forkHeader);
        cache.addVote1(height, round, hash, self.getAgent().getPackingAddress(), time);

    }

    private void signAndBroadcast(MeetingMember self, VoteMessage message) {
        //签名
        byte[] sign = new byte[0];
        try {
            sign = CallMethodUtils.signature(chain, AddressTool.getStringAddressByBytes(self.getAgent().getPackingAddress()), Sha256Hash.hash(message.serializeForDigest()));
        } catch (NulsException e) {
            LoggerUtil.commonLog.error(e);
        } catch (IOException e) {
            LoggerUtil.commonLog.error(e);
        }
        message.setSign(sign);

        CallMethodUtils.broadcastMsg(chainId, message, PocMessageType.VOTE_MESSAGE);
    }

    public void recieveVote(String nodeId, VoteMessage message, byte[] address) {
        LoggerUtil.commonLog.info("=======Vote:{} ,{} address:{}", message.getHeight(), message.getHash(), AddressTool.getStringAddressByBytes(address));
        // 判断是否接受此投票
        if (curRound.getHeight() > message.getHeight() || (curRound.getHeight() == message.getHeight() && curRound.getRound() > message.getRound())) {
            return;
        }
        if (cache.contains(message, address)) {
            return;
        }
        // 如果分叉需要特别处理
        if (null == message.getHash() && (message.getHeader1() != null || message.getHeader2() != null)) {
            if (null == message.getHeader2()) {
                return;
            }
            if (null == message.getHeader1()) {
                return;
            }
            if (message.getHeader1().getHeight() != message.getHeader2().getHeight()) {
                return;
            }
            if (!ArraysTool.arrayEquals(message.getHeader2().getPackingAddress(chainId), message.getHeader1().getPackingAddress(chainId))) {
                return;
            }
            /// TODO: 2019-06-21
            System.out.println("Wait to fix ...Wait to fix ...Wait to fix ...Wait to fix ...Wait to fix ...");

        }
        // 广播签名
        CallMethodUtils.broadcastMsg(chainId, message, nodeId, PocMessageType.VOTE_MESSAGE);

        long time = message.getStartTime();

        int totalCount = pocRound.getMemberCount();


        byte step = message.getStep();
        PbftData pbftData;
        if (step < 1) {
            pbftData = cache.addVote1(message.getHeight(), message.getRound(), message.getHash(), address, time);
            VoteResultItem result = pbftData.getVote1LargestItem();
            //判断自己是否需要签名，如果需要就直接进行
            MeetingMember self = pocRound.getMyMember();
            if (result.getCount() > VoteConstant.DEFAULT_RATE * totalCount && !pbftData.hasVoted2(self.getAgent().getPackingAddress())) {
                VoteMessage msg = new VoteMessage();
                msg.setHeight(message.getHeight());
                msg.setRound(message.getRound());
                msg.setStep((byte) 1);
                msg.setHash(result.getHash());
                this.signAndBroadcast(self, message);
                cache.addVote2(message.getHeight(), message.getRound(), message.getHash(), self.getAgent().getPackingAddress(), time);
            }
        } else {
            pbftData = cache.addVote2(message.getHeight(), message.getRound(), message.getHash(), address, time);
            VoteResultItem result = pbftData.getVote2LargestItem();
            if (result.getCount() > VoteConstant.DEFAULT_RATE * totalCount) {
                this.sureResult(pbftData.getHeight(), result.getHash(), pocRound);
            }
        }

    }
}
