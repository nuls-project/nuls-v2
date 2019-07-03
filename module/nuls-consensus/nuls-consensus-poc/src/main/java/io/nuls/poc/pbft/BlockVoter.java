package io.nuls.poc.pbft;

import io.nuls.base.basic.AddressTool;
import io.nuls.base.data.Block;
import io.nuls.base.data.BlockExtendsData;
import io.nuls.base.data.BlockHeader;
import io.nuls.base.data.NulsHash;
import io.nuls.core.constant.ErrorCode;
import io.nuls.core.core.ioc.SpringLiteContext;
import io.nuls.core.exception.NulsException;
import io.nuls.core.model.ArraysTool;
import io.nuls.core.model.DateUtils;
import io.nuls.core.rpc.util.NulsDateUtils;
import io.nuls.poc.constant.ConsensusErrorCode;
import io.nuls.poc.constant.PocMessageType;
import io.nuls.poc.model.bo.Chain;
import io.nuls.poc.model.bo.round.MeetingMember;
import io.nuls.poc.model.bo.round.MeetingRound;
import io.nuls.poc.pbft.cache.BlockProuceKeyCache;
import io.nuls.poc.pbft.cache.PreCommitCache;
import io.nuls.poc.pbft.cache.VoteCenter;
import io.nuls.poc.pbft.constant.VoteConstant;
import io.nuls.poc.pbft.message.VoteMessage;
import io.nuls.poc.pbft.model.*;
import io.nuls.poc.rpc.call.CallMethodUtils;
import io.nuls.poc.utils.LoggerUtil;
import io.nuls.poc.utils.enumeration.ConsensusStatus;
import io.nuls.poc.utils.manager.RoundManager;

import java.io.IOException;
import java.util.Date;
import java.util.List;
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
                if (!this.checkConsensusStatus(this.chain) || null == this.roundManager) {
                    Thread.sleep(1000L);
                    continue;
                }
                long sleep = 100L;
                if (null == pocRound) {
                    this.roundManager.initRound(chain);
                    this.pocRound = roundManager.getCurrentRound(chain);
                    chain.getLogger().debug("\n初始化轮次||index:{},startTime:{},currentIndex:{},\n" + pocRound.toString(), pocRound.getIndex(), DateUtils.timeStamp2DateStr(pocRound.getStartTime() * 1000), pocRound.getCurrentMemberIndex());
                }
                if (null != pocRound && pocRound.getMyMember() != null) {
                    try {
                        lock.lock();
                        doit();
                    } finally {
                        lock.unlock();
                    }
                } else {
                    List<byte[]> packingAddressList = CallMethodUtils.getEncryptedAddressList(chain);
                    if (!packingAddressList.isEmpty()) {
                        this.pocRound.calcLocalPacker(packingAddressList, chain);
                    }
                    sleep = 1000L;
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

    /**
     * 检查节点状态
     * Check node packing status
     */
    private boolean checkConsensusStatus(Chain chain) throws Exception {
        if (chain == null) {
            throw new NulsException(ConsensusErrorCode.CHAIN_NOT_EXIST);
        }
        /*
        检查模块状态是否为运行中
        Check whether the module status is in operation
        */
        if (chain.getConsensusStatus().ordinal() <= ConsensusStatus.WAIT_RUNNING.ordinal()) {
            return false;
        }

        /*
        检查节点状态是否可打包(区块管理模块同步完成之后设置该状态)
        Check whether the node status can be packaged (set up after the block management module completes synchronization)
        */
        if (!chain.isCanPacking()) {
            return false;
        }
        return true;
    }

    private void doit() {
        if (this.pocRound.getCurrentMemberIndex() > this.pocRound.getMemberCount()) {
            return;
        }
        long now = NulsDateUtils.getCurrentTimeSeconds();
        this.lastHeader = chain.getNewestHeader();
        if (now < this.timeout + this.lastHeader.getTime()) {
            return;
        }

        if (this.pocRound.getCurVoteRound() == null) {
            long offset = now - lastHeader.getTime();
            if (offset < 0) {
                offset = 0;
            }
            long round = offset / this.timeout;
            changeCurrentRound(round, lastHeader.getTime() + round * this.timeout);
            return;
        }

        if (now < this.pocRound.getCurVoteRound().getStart()) {
            return;
        }
        PbftData pbftData = cache.getCurrentResult(this.pocRound.getCurVoteRound().getHeight(), this.pocRound.getCurVoteRound().getTimes());
        if (null != pbftData) {
            VoteResultItem result = pbftData.getVote2LargestItem();
            if (result.getCount() > VoteConstant.DEFAULT_RATE * pocRound.getMemberCount() && result.getHash() != null) {
                if ((result.getHash().equals(NulsHash.EMPTY_NULS_HASH) && pocRound.getCurVoteRound().getEnd() <= NulsDateUtils.getCurrentTimeSeconds()) || this.lastHeader.getHeight() >= pocRound.getCurVoteRound().getHeight()) {
                    long offset = now - lastHeader.getTime();
                    if (offset < 0) {
                        offset = 0;
                    }
                    long round = offset / this.timeout;
                    changeCurrentRound(round, lastHeader.getTime() + round * this.timeout);
                }
                return;
            }
        }

        if (now < this.pocRound.getCurVoteRound().getEnd() + this.timeout / 2) {
            return;
        }
        long start = this.pocRound.getCurVoteRound().getStart();
        int round = this.pocRound.getCurVoteRound().getTimes();
        if (preCommitCache.getHeight() != this.pocRound.getCurVoteRound().getHeight()) {
            preCommitCache.clear();
        }
        if (preCommitCache.getForkHeader() != null) {
            LoggerUtil.commonLog.info("====投票给分叉");
            preCommitVote(this.pocRound.getCurVoteRound().getHeight(), round, NulsHash.EMPTY_NULS_HASH, preCommitCache.getHeader(), start, preCommitCache.getForkHeader(), pocRound.getMyMember());
        } else {
            LoggerUtil.commonLog.info("====超时投票：{},height: {},round: {}", preCommitCache.getShouldNext().toHex(), this.pocRound.getCurVoteRound().getHeight(), this.pocRound.getCurVoteRound().getTimes());
            this.preCommitVote(this.pocRound.getCurVoteRound().getHeight(), (int) round, preCommitCache.getShouldNext(), preCommitCache.getHeader(), start, null, pocRound.getMyMember());
        }
        now = NulsDateUtils.getCurrentTimeSeconds();
        long offset = now - lastHeader.getTime();
        if (offset < 0) {
            offset = 0;
        }
        round = (int) (offset / this.timeout);
        this.changeCurrentRound(round, lastHeader.getTime() + round * this.timeout);
    }

    private void changeCurrentRound(long round, long startTime) {
//        LoggerUtil.commonLog.info("-_-_-计算：round：" + round + ", start:" + startTime);
        VoteRound curRound = new VoteRound();
        curRound.setHeight(this.lastHeader.getHeight() + 1);
        curRound.setTimes((int) round);
        curRound.setStart(startTime - 1);
        curRound.setEnd(startTime - 1 + this.timeout);
        LoggerUtil.commonLog.info("{}--NEW ROUND==height:{},round:{},startTime:{}, endtime:{}", DateUtils.timeStamp2DateStr(NulsDateUtils.getCurrentTimeMillis()), curRound.getHeight(), curRound.getTimes(), new Date(curRound.getStart() * 1000).toLocaleString(), new Date(curRound.getEnd() * 1000).toLocaleString());
        LoggerUtil.commonLog.info("{}--POC ROUND::index:{},start:{}", DateUtils.timeStamp2DateStr(NulsDateUtils.getCurrentTimeMillis()), pocRound.getIndex(), DateUtils.timeStamp2DateStr(pocRound.getStartTime() * 1000));
        pocRound.setCurVoteRound(curRound);
    }

    private void sureResult(long height, NulsHash hash, BlockHeader bestBlockHeader, long time) {
        //因为投票轮次和poc轮次有1s的偏移
        long startTime = time + 1 + this.timeout;
        LoggerUtil.commonLog.info("=======确认区块：{}, {}", height, hash.toString());
        boolean result = CallMethodUtils.sendVerifyResult(chainId, height, hash);
        if (!result) {
            return;
        }
        this.preCommitCache.clear();
        pocRound.setCurrentMemberIndex(pocRound.getCurrentMemberIndex() + 1);
        if (pocRound.getCurrentMemberIndex() > pocRound.getMemberCount()) {
            try {
                if (null == bestBlockHeader) {
                    bestBlockHeader = chain.getNewestHeader();
                }
                this.pocRound = roundManager.createNextRound(chain, bestBlockHeader, pocRound.getIndex() + 1, startTime, pocRound);
            } catch (Exception e) {
                LoggerUtil.commonLog.error(e);
            }
        }
        if (null == pocRound.getMyMember()) {
            return;
        }
        byte[] address = pocRound.getMyMember().getAgent().getPackingAddress();
        byte[] packer = pocRound.getMember(pocRound.getCurrentMemberIndex()).getAgent().getPackingAddress();
        if (!ArraysTool.arrayEquals(address, packer)) {
            return;
        }
        ProduceKey key = new ProduceKey();
        key.setAddress(address);
        key.setHeight(hash.equals(NulsHash.EMPTY_NULS_HASH) ? height : height + 1);
        key.setPrehash(hash.equals(NulsHash.EMPTY_NULS_HASH) ? this.lastHeader.getHash() : hash);
        key.setStartTime(startTime);
        key.setEndTime(key.getStartTime() + this.timeout);
        key.setIndexOfRound(pocRound.getCurrentMemberIndex());
        key.setSelf(pocRound.getMyMember());
        key.getSelf().setStartTime(key.getStartTime());
        key.setEndTime(key.getEndTime());
        key.setRound(pocRound);
        LoggerUtil.commonLog.info("key info:height:{}, startTime:{} ,end:{}", key.getHeight(), DateUtils.timeStamp2DateStr(key.getStartTime() * 1000), DateUtils.timeStamp2DateStr(key.getEndTime() * 1000));
        BlockProuceKeyCache.PRODUCE_KEYS_QUEUE.offer(key);
    }

    public ErrorCode recieveBlock(Block block) {
        LoggerUtil.commonLog.info("receive block:" + block.getHeader().getHash().toHex());
        try {
            lock.lock();
            return this.realRecieveBlock(block);
        } catch (NulsException e) {
            LoggerUtil.commonLog.error(e);
            return e.getErrorCode();
        } finally {
            lock.unlock();
        }
    }

    private ErrorCode realRecieveBlock(Block block) throws NulsException {
        this.initRound();
        ErrorCode code = ConsensusErrorCode.WAIT_BLOCK_VERIFY;
        long height = block.getHeader().getHeight();
        if (height != this.pocRound.getCurVoteRound().getHeight()) {
            return code;
        }
        this.cache.putBlockHeader(block.getHeader());
        NulsHash hash = block.getHeader().getHash();
        BlockExtendsData extendsData = new BlockExtendsData();
        extendsData.parse(block.getHeader().getExtend(), 0);
        LoggerUtil.commonLog.info("blockVote:height:" + this.pocRound.getCurVoteRound().getHeight() + ",round:" + pocRound.getCurVoteRound().getTimes() + ",address:" + AddressTool.getStringAddressByBytes(block.getHeader().getPackingAddress(chainId)) + ", hash:" + block.getHeader().getHash().toHex());
        PbftData pbftData = cache.addVote1(height, this.pocRound.getCurVoteRound().getTimes(), hash, AddressTool.getStringAddressByBytes(block.getHeader().getPackingAddress(chainId)), false, extendsData.getRoundIndex(), extendsData.getRoundStartTime(), extendsData.getPackingIndexOfRound());
        //判断自己是否需要签名，如果需要就直接进行
        MeetingMember self = pocRound.getMyMember();
        if (null != self && ArraysTool.arrayEquals(self.getAgent().getPackingAddress(), block.getHeader().getPackingAddress(chainId))) {
            this.preCommitCache.change(height, pocRound.getCurVoteRound().getTimes(), hash, block.getHeader(), null);
        }
        int totalCount = pocRound.getMemberCount();

        VoteData voteData = null == self ? null : pbftData.hasVoted1(self.getAgent().getPackingAddress());
        if (null != self && (null == voteData || (voteData.getHash() == NulsHash.EMPTY_NULS_HASH && !voteData.isBifurcation()))) {
            LoggerUtil.commonLog.info("===投票给一个区块：{}, {}", block.getHeader().getHeight(), block.getHeader().getHash().toString());
            preCommitVote(height, this.pocRound.getCurVoteRound().getTimes(), block.getHeader().getHash(), block.getHeader(), this.lastHeader.getTime() + this.timeout * (this.pocRound.getCurVoteRound().getTimes() - 1), null, self);
        }
        VoteResultItem result = pbftData.getVote1LargestItem();
        if (result.getCount() > VoteConstant.DEFAULT_RATE * totalCount && null == pbftData.hasVoted2(self.getAgent().getPackingAddress())) {
            VoteMessage message = new VoteMessage();
            message.setHeight(height);
            message.setTimes(pbftData.getTimes());
            message.setStep((byte) 1);
            message.setBlockHash(result.getHash());
//            message.setRoundIndex(extendsData.getRoundIndex());
//            message.setRoundStartTime(extendsData.getRoundStartTime());
//            message.setCurrentMemberIndex(extendsData.getPackingIndexOfRound());
            this.signAndBroadcast(self, message);
            LoggerUtil.commonLog.info("====commit轮投票：{} ,{}", height, message.getBlockHash().toString());
            pbftData = cache.addVote2(height, pbftData.getTimes(), message.getBlockHash(), AddressTool.getStringAddressByBytes(self.getAgent().getPackingAddress()));
        }
        if (result.getCount() > VoteConstant.DEFAULT_RATE * totalCount) {
            this.mkSureRound(result.getRoundIndex(), result.getRoundStartTime(), result.getCurrentMemberIndex());
        }
        result = pbftData.getVote2LargestItem();

        if (result.getCount() > VoteConstant.DEFAULT_RATE * totalCount) {
            this.sureResult(height, result.getHash(), cache.getBlockHeader(height, result.getHash()), this.pocRound.getCurVoteRound().getStart());
        }
        return code;
    }

    private void mkSureRound(long roundIndex, long roundStartTime, int currentMemberIndex) {
        if (pocRound.getIndex() > roundIndex) {
            return;
        }
        if (pocRound.getIndex() == roundIndex && roundStartTime == pocRound.getStartTime() && this.pocRound.getCurrentMemberIndex() == currentMemberIndex) {
            return;
        }
        if (pocRound.getIndex() == roundIndex) {
            this.roundManager.clearRound(chain, pocRound.getIndex());
        }
        try {
            LoggerUtil.commonLog.info("@@@@@index:{},start:{},");
            this.pocRound = roundManager.createNextRound(chain, chain.getNewestHeader(), roundIndex, roundStartTime, pocRound);
        } catch (Exception e) {
            LoggerUtil.commonLog.error(e);
            return;
        }
        pocRound.setCurrentMemberIndex(currentMemberIndex);
        chain.getLogger().debug("\n初始化轮次||index:{},startTime:{},currentIndex:{},\n" + pocRound.toString(), pocRound.getIndex(), DateUtils.timeStamp2DateStr(pocRound.getStartTime() * 1000), pocRound.getCurrentMemberIndex());
    }

    private void preCommitVote(long height, int round, NulsHash hash, BlockHeader header, long time, BlockHeader forkHeader, MeetingMember self) {
//        LoggerUtil.commonLog.info("localVote:" + hash.toHex());
        VoteMessage message = new VoteMessage();
        message.setHeight(height);
        message.setTimes(round);
        message.setStep((byte) 0);
        message.setBlockHash(hash);
        boolean bifurcation = false;
        if (null != forkHeader) {
            bifurcation = true;
            message.setHeader1(header);
            message.setHeader2(forkHeader);
        }
        this.signAndBroadcast(self, message);
        this.preCommitCache.change(height, round, preCommitCache.getShouldNext(), header, forkHeader);
        PbftData pbftData = cache.addVote1(height, round, hash, AddressTool.getStringAddressByBytes(self.getAgent().getPackingAddress()), bifurcation, pocRound.getIndex(), pocRound.getStartTime(), pocRound.getCurrentMemberIndex());
        VoteResultItem result = pbftData.getVote1LargestItem();
        //判断自己是否需要签名，如果需要就直接进行
        if (result.getCount() > VoteConstant.DEFAULT_RATE * this.pocRound.getMemberCount() && null == pbftData.hasVoted2(self.getAgent().getPackingAddress())) {
            VoteMessage msg = new VoteMessage();
            msg.setHeight(message.getHeight());
            msg.setTimes(message.getTimes());
            msg.setStep((byte) 1);
            msg.setBlockHash(result.getHash());
            this.signAndBroadcast(self, msg);
            cache.addVote2(message.getHeight(), message.getTimes(), message.getBlockHash(), AddressTool.getStringAddressByBytes(self.getAgent().getPackingAddress()));
        }

        result = pbftData.getVote2LargestItem();
        if (result.getCount() > VoteConstant.DEFAULT_RATE * this.pocRound.getMemberCount()) {
            this.sureResult(height, result.getHash(), cache.getBlockHeader(height, result.getHash()), time);
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
//        System.out.println("myRound: height=" + pocRound.getCurVoteRound().getHeight() + ", round:" + pocRound.getCurVoteRound().getTimes());
        LoggerUtil.commonLog.info("receive: height=" + message.getHeight() + ", round:" + message.getTimes() + ",hash:" + message.getBlockHash().toHex() + ", address:" + message.getAddress(this.chainId));
        // 判断是否接受此投票
        if (pocRound.getCurVoteRound().getHeight() > message.getHeight() || (pocRound.getCurVoteRound().getHeight() == message.getHeight() && pocRound.getCurVoteRound().getTimes() > message.getTimes() + 1)) {
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

        int totalCount = pocRound.getMemberCount();
        byte step = message.getStep();
        PbftData pbftData;
        VoteResultItem realResult;
        BlockHeader header = cache.getBlockHeader(message.getHeight(), message.getBlockHash());
        long roundIndex = pocRound.getIndex();
        long roundStartTime = pocRound.getStartTime();
        int currentMemberIndex = pocRound.getCurrentMemberIndex();
        if (null != header) {
            BlockExtendsData extendsData = new BlockExtendsData(header.getExtend());
            roundIndex = extendsData.getRoundIndex();
            roundStartTime = extendsData.getRoundStartTime();
            currentMemberIndex = extendsData.getPackingIndexOfRound();
        }
        if (step == 0) {
            //todo 判断是否存在该区块，不存在则下载
            pbftData = cache.addVote1(message.getHeight(), message.getTimes(), message.getBlockHash(), address, message.getBlockHash() == null && message.getHeader2() != null, roundIndex, roundStartTime, currentMemberIndex);
            VoteResultItem result = pbftData.getVote1LargestItem();
            //判断自己是否需要签名，如果需要就直接进行
            MeetingMember self = pocRound.getMyMember();
            if (null != self && result.getCount() > VoteConstant.DEFAULT_RATE * totalCount && null == pbftData.hasVoted2(self.getAgent().getPackingAddress())) {
                VoteMessage msg = new VoteMessage();
                msg.setHeight(message.getHeight());
                msg.setTimes(message.getTimes());
                msg.setStep((byte) 1);
                msg.setBlockHash(result.getHash());
                this.signAndBroadcast(self, msg);
                cache.addVote2(message.getHeight(), message.getTimes(), message.getBlockHash(), AddressTool.getStringAddressByBytes(self.getAgent().getPackingAddress()));
            }
            if (null != self && result.getCount() > VoteConstant.DEFAULT_RATE * totalCount) {
                this.mkSureRound(result.getRoundIndex(), result.getRoundStartTime(), result.getCurrentMemberIndex());
            }
            realResult = pbftData.getVote2LargestItem();
        } else {
            //todo 判断是否认同，不认同则下载数据
            pbftData = cache.addVote2(message.getHeight(), message.getTimes(), message.getBlockHash(), address);
            realResult = pbftData.getVote2LargestItem();
        }
        if (realResult.getCount() > VoteConstant.DEFAULT_RATE * totalCount) {
            long time = pocRound.getCurVoteRound().getStart() - this.timeout * (pocRound.getCurVoteRound().getTimes() - pbftData.getTimes());
            this.sureResult(pbftData.getHeight(), realResult.getHash(), cache.getBlockHeader(pbftData.getHeight(), realResult.getHash()), time);
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
        if (null == this.pocRound.getCurVoteRound() || this.pocRound.getCurVoteRound().getHeight() <= this.lastHeader.getHeight()) {
            long offset = now - lastHeader.getTime();
            if (offset < 0) {
                offset = 0;
            }
            long round = offset / this.timeout;
            changeCurrentRound(round, lastHeader.getTime() + round * this.timeout);
        }
    }
}
