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
import io.nuls.poc.pbft.model.VoteData;
import io.nuls.poc.pbft.model.VoteResultItem;
import io.nuls.poc.pbft.model.VoteRound;
import io.nuls.poc.rpc.call.CallMethodUtils;
import io.nuls.poc.utils.LoggerUtil;
import io.nuls.poc.utils.manager.RoundManager;

import java.io.IOException;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

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

    private BlockHeader lastHeader;
    private MeetingRound pocRound;

    private Lock lock = new ReentrantLock();

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
                long sleep = 100L;
                if (null == pocRound || pocRound.getMemberCount() <= (1 + pocRound.getCurrentMemberIndex())) {
                    this.pocRound = this.roundManager.getCurrentRound(chain);
                }

                if (null != pocRound && pocRound.getMyMember() != null) {
                    try {
                        lock.lock();
                        doit(pocRound);
                    } finally {
                        lock.unlock();
                    }
                } else {
                    sleep = 1000L;
                    this.pocRound = this.roundManager.getCurrentRound(chain);
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
        if (this.pocRound.getCurrentMemberIndex() > this.pocRound.getMemberCount()) {
            return;
        }
        long now = NulsDateUtils.getCurrentTimeSeconds();
        this.lastHeader = chain.getNewestHeader();
        if (now < this.timeout + this.lastHeader.getTime()) {
            return;
        }
        MeetingMember meetingMember = pocRound.getMember(pocRound.getCurrentMemberIndex());
        long offset = now - lastHeader.getTime() - this.timeout;
        if (offset < 0) {
            offset = 0;
        }
        long round = offset / this.timeout + 1;
        if (this.pocRound.getCurVoteRound() == null) {
            changeCurrentRound(round, lastHeader.getTime() + round * this.timeout + timeout);
            return;
        }
//        System.out.println("now:" + now);
//        System.out.println("member:" + meetingMember.getEndTime());
//        System.out.println("offsset:" + pocRound.getOffset());
//        System.out.println("round:" + this.pocRound.getCurVoteRound().getRound());
//        System.out.println("end::" + this.pocRound.getCurVoteRound().getEnd());
//        System.out.println("==================================================");

        if (now < this.pocRound.getCurVoteRound().getStart()) {
            return;
        }
        PbftData pbftData = cache.getCurrentResult(this.pocRound.getCurVoteRound().getHeight(), this.pocRound.getCurVoteRound().getRound());
        if (null != pbftData) {
            VoteResultItem result = pbftData.getVote2LargestItem();
            if (result.getCount() > VoteConstant.DEFAULT_RATE * pocRound.getMemberCount() && result.getHash() != null) {
                return;
            }
        }

        if (now < this.pocRound.getCurVoteRound().getEnd()) {
            return;
        }
        this.changeCurrentRound(round, lastHeader.getTime() + round * this.timeout + timeout);
        long start = this.pocRound.getCurVoteRound().getEnd();
        if (preCommitCache.getForkHeader() != null) {
//            LoggerUtil.commonLog.info("====投票给分叉");
            preCommitVote(this.pocRound.getCurVoteRound().getHeight(), (int) round, preCommitCache.getShouldNext(), preCommitCache.getHeader(), start, preCommitCache.getForkHeader(), pocRound.getMyMember());
        } else {
            pocRound.setOffset(pocRound.getOffset() + this.timeout);
            LoggerUtil.commonLog.info("====投票给空块：{},round:{}", this.pocRound.getCurVoteRound().getHeight(), this.pocRound.getCurVoteRound().getRound());
            this.preCommitVote(this.pocRound.getCurVoteRound().getHeight(), (int) round, NulsHash.EMPTY_NULS_HASH, null, start, null, pocRound.getMyMember());
        }
    }

    private void changeCurrentRound(long round, long startTime) {
//        LoggerUtil.commonLog.info("-_-_-计算：round：" + round + ", start:" + startTime);
        VoteRound curRound = new VoteRound();
        curRound.setHeight(this.lastHeader.getHeight() + 1);
        curRound.setRound((int) round);
        curRound.setStart(startTime);
        curRound.setEnd(startTime + this.timeout);
        pocRound.setCurVoteRound(curRound);
    }

    private void sureResult(long height, NulsHash hash, MeetingRound pocRound) {
        LoggerUtil.commonLog.info("=======确认区块：{}, {}", height, hash.toString());
        boolean result = CallMethodUtils.sendVerifyResult(chainId, height, hash);
        if (result) {
            this.preCommitCache.clear(hash);
            pocRound.setCurrentMemberIndex(pocRound.getCurrentMemberIndex() + 1);
        }
    }

    public ErrorCode recieveBlock(Block block) {
        try {
            lock.lock();
            return this.realRecieveBlock(block);
        } finally {
            lock.unlock();
        }
    }

    private ErrorCode realRecieveBlock(Block block) {
        this.initRound();
        ErrorCode code = ConsensusErrorCode.WAIT_BLOCK_VERIFY;
        long height = block.getHeader().getHeight();
        if (height != this.pocRound.getCurVoteRound().getHeight()) {
            return code;
        }
        NulsHash hash = block.getHeader().getHash();
//        LoggerUtil.commonLog.info("blockVote:" + AddressTool.getStringAddressByBytes(block.getHeader().getPackingAddress(chainId)) + ", hash:" + block.getHeader().getHash().toHex());
        PbftData pbftData = cache.addVote1(height, this.pocRound.getCurVoteRound().getRound(), hash, AddressTool.getStringAddressByBytes(block.getHeader().getPackingAddress(chainId)), block.getHeader().getTime(), false);

        int totalCount = pocRound.getMemberCount();
//        if (totalCount == 1) {
//            code = ConsensusErrorCode.SUCCESS;
//            return code;
//        }
        //判断自己是否需要签名，如果需要就直接进行
        MeetingMember self = pocRound.getMyMember();
        VoteData voteData = null == self ? null : pbftData.hasVoted1(self.getAgent().getPackingAddress());
        if (null != self && (null == voteData || (voteData.getHash() == null && !voteData.isBifurcation()))) {
            LoggerUtil.commonLog.info("===投票给一个区块：{}, {}", block.getHeader().getHeight(), block.getHeader().getHash().toString());
            preCommitVote(height, this.pocRound.getCurVoteRound().getRound(), block.getHeader().getHash(), block.getHeader(), this.lastHeader.getTime() + this.timeout * (this.pocRound.getCurVoteRound().getRound() - 1), null, self);
        }
        VoteResultItem result = pbftData.getVote1LargestItem();
        if (result.getCount() > VoteConstant.DEFAULT_RATE * totalCount && null == pbftData.hasVoted2(self.getAgent().getPackingAddress())) {
            VoteMessage message = new VoteMessage();
            message.setHeight(height);
            message.setRound(pbftData.getRound());
            message.setStep((byte) 1);
            message.setStartTime(block.getHeader().getTime());
            message.setBlockHash(result.getHash());
            this.signAndBroadcast(self, message);
            LoggerUtil.commonLog.info("====commit轮投票：{} ,{}", height, message.getBlockHash().toString());
            pbftData = cache.addVote2(height, pbftData.getRound(), message.getBlockHash(), AddressTool.getStringAddressByBytes(self.getAgent().getPackingAddress()), block.getHeader().getTime());

        }
        result = pbftData.getVote2LargestItem();

        if (result.getCount() > VoteConstant.DEFAULT_RATE * totalCount) {
            this.sureResult(height, result.getHash(), this.pocRound);
        }
        return code;
    }

    private void preCommitVote(long height, int round, NulsHash hash, BlockHeader header, long time, BlockHeader forkHeader, MeetingMember self) {
        LoggerUtil.commonLog.info("localVote:" + hash.toHex());
        VoteMessage message = new VoteMessage();
        message.setHeight(height);
        message.setRound(round);
        message.setStep((byte) 0);
        message.setStartTime(time);
        message.setBlockHash(hash);
        boolean bifurcation = false;
        if (null != forkHeader) {
            bifurcation = true;
            message.setHeader1(header);
            message.setHeader2(forkHeader);
        }
        this.signAndBroadcast(self, message);
        this.preCommitCache.change(height, round, preCommitCache.getShouldNext(), header, forkHeader);
        PbftData pbftData = cache.addVote1(height, round, hash, AddressTool.getStringAddressByBytes(self.getAgent().getPackingAddress()), time, bifurcation);
        VoteResultItem result = pbftData.getVote1LargestItem();
        //判断自己是否需要签名，如果需要就直接进行
        if (result.getCount() > VoteConstant.DEFAULT_RATE * this.pocRound.getMemberCount() && null == pbftData.hasVoted2(self.getAgent().getPackingAddress())) {
            VoteMessage msg = new VoteMessage();
            msg.setHeight(message.getHeight());
            msg.setRound(message.getRound());
            msg.setStep((byte) 1);
            msg.setBlockHash(result.getHash());
            this.signAndBroadcast(self, msg);
            cache.addVote2(message.getHeight(), message.getRound(), message.getBlockHash(), AddressTool.getStringAddressByBytes(self.getAgent().getPackingAddress()), time);
        }
    }

    private void signAndBroadcast(MeetingMember self, VoteMessage message) {
        //签名
        byte[] sign = new byte[0];
        try {
            sign = CallMethodUtils.signature(chain, AddressTool.getStringAddressByBytes(self.getAgent().getPackingAddress()), NulsHash.calcHash(message.serializeForDigest()).getBytes());
        } catch (NulsException e) {
            LoggerUtil.commonLog.error(e);
        } catch (IOException e) {
            LoggerUtil.commonLog.error(e);
        }
        message.setSign(sign);

        CallMethodUtils.broadcastMsg(chainId, message, PocMessageType.VOTE_MESSAGE);
    }

    public void recieveVote(String nodeId, VoteMessage message, String address) {
        try {
            lock.lock();
            this.realRecieveVote(nodeId, message, address);
        } finally {
            lock.unlock();
        }
    }

    private void realRecieveVote(String nodeId, VoteMessage message, String address) {
        this.initRound();
        if (null == this.pocRound || this.pocRound.getCurVoteRound() == null) {
            return;
        }
//        LoggerUtil.commonLog.info("======Receive=Vote:{} ,{} address:{}", message.getHeight(), message.getBlockHash(), address);
//        System.out.println("myRound: height=" + pocRound.getCurVoteRound().getHeight() + ", round:" + pocRound.getCurVoteRound().getRound());
//        System.out.println("receive: height=" + message.getHeight() + ", round:" + message.getRound() + ",hash:" + message.getBlockHash().toHex() + ", address:" + message.getAddress(this.chainId));
        // 判断是否接受此投票
        if (pocRound.getCurVoteRound().getHeight() > message.getHeight() || (pocRound.getCurVoteRound().getHeight() == message.getHeight() && pocRound.getCurVoteRound().getRound() > message.getRound() + 1)) {
            return;
        }
        if (cache.contains(message, address)) {
            return;
        }
        // 如果分叉需要特别处理
        if (null == message.getBlockHash() && (message.getHeader1() != null || message.getHeader2() != null)) {
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
        VoteResultItem realResult;
        if (step == 0) {
            pbftData = cache.addVote1(message.getHeight(), message.getRound(), message.getBlockHash(), address, time, message.getBlockHash() == null && message.getHeader2() != null);
            VoteResultItem result = pbftData.getVote1LargestItem();
            //判断自己是否需要签名，如果需要就直接进行
            MeetingMember self = pocRound.getMyMember();
            if (null != self && result.getCount() > VoteConstant.DEFAULT_RATE * totalCount && null == pbftData.hasVoted2(self.getAgent().getPackingAddress())) {
                VoteMessage msg = new VoteMessage();
                msg.setHeight(message.getHeight());
                msg.setRound(message.getRound());
                msg.setStep((byte) 1);
                msg.setBlockHash(result.getHash());
                this.signAndBroadcast(self, msg);
                cache.addVote2(message.getHeight(), message.getRound(), message.getBlockHash(), AddressTool.getStringAddressByBytes(self.getAgent().getPackingAddress()), time);
            }
            realResult = pbftData.getVote2LargestItem();
        } else {
            pbftData = cache.addVote2(message.getHeight(), message.getRound(), message.getBlockHash(), address, time);
            realResult = pbftData.getVote2LargestItem();
        }
        if (realResult.getCount() > VoteConstant.DEFAULT_RATE * totalCount) {
            this.sureResult(pbftData.getHeight(), realResult.getHash(), pocRound);
        }

    }

    private void initRound() {
        long now = NulsDateUtils.getCurrentTimeSeconds();
        this.lastHeader = chain.getNewestHeader();
        if (this.pocRound == null) {
            this.pocRound = this.roundManager.getCurrentRound(chain);
        }
        if (null == this.pocRound) {
            return;
        }
        if (null == this.pocRound.getCurVoteRound()) {
            long offset = now - lastHeader.getTime() - this.timeout;
            if (offset < 0) {
                offset = 0;
            }
            long round = offset / this.timeout + 1;
            if (this.pocRound.getCurVoteRound() == null) {
                changeCurrentRound(round, lastHeader.getTime() + round * this.timeout + timeout);
                return;
            }
        }
    }
}
