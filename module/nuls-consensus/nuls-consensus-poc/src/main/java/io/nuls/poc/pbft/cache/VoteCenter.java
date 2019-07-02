package io.nuls.poc.pbft.cache;

import io.nuls.base.data.BlockHeader;
import io.nuls.base.data.NulsHash;
import io.nuls.poc.pbft.message.VoteMessage;
import io.nuls.poc.pbft.model.PbftData;
import io.nuls.poc.pbft.model.VoteData;
import io.nuls.poc.utils.LoggerUtil;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Niels
 */
public class VoteCenter {

    private final int chainId;

    private Map<Long, Map<NulsHash, BlockHeader>> headerMap = new HashMap<>();

    private Map<String, PbftData> map = new HashMap<>();

    public VoteCenter(int chainId) {
        this.chainId = chainId;
    }

    public PbftData addVote1(long height, long round, NulsHash hash, String address, boolean bifurcation, long roundIndex, long roundStartTime, int currentMemberIndex) {
//        LoggerUtil.commonLog.info("====height:{}, round:{}, hash:{}, address:{}", height, round, hash.toString(), address);
        PbftData pbftData = getPbftData(height, round, roundIndex, roundStartTime, currentMemberIndex);
        VoteData voteData = pbftData.getVote1ByAddress(address);
        VoteData data;
        if (null != voteData) {
            if (voteData.getHash() != null && hash == null && !bifurcation) {
                //todo 恶意修改投票结果
            } else if (voteData.getHash() == null && voteData.isBifurcation() && !bifurcation) {
                //todo 恶意修改投票结果
            }
            data = voteData;
        } else {
            data = new VoteData();
        }
        data.setAddress(address);
        data.setHash(hash);
        data.setHeight(height);
        data.setRound(round);
        data.setBifurcation(bifurcation);
        pbftData.addVote1Result(data);


        return pbftData;
    }

    public PbftData addVote2(long height, long round, NulsHash hash, String address, long roundIndex, long roundStartTime, int currentMemberIndex) {
        LoggerUtil.commonLog.info("step 2 : height:{}, round:{}, hash:{}, address:{}", height, round, null == hash ? hash : hash.toString(), address);
        PbftData pbftData = getPbftData(height, round, roundIndex, roundStartTime, currentMemberIndex);

        //todo 收集恶意数据


        VoteData data = new VoteData();
        data.setAddress(address);
        data.setHash(hash);
        data.setHeight(height);
        data.setRound(round);
        pbftData.addVote2Result(data);
        return pbftData;
    }

    private PbftData getPbftData(long height, long times, long roundIndex, long roundStartTime, int currentMemberIndex) {
        String key = height + "_" + times;
        PbftData data = map.get(key);
        if (null == data) {
            data = new PbftData();
            data.setHeight(height);
            data.setTimes(times);
            data.setRoundIndex(roundIndex);
            data.setRoundStartTime(roundStartTime);
            data.setCurrentMemberIndex(currentMemberIndex);
            map.put(key, data);
        }
        return data;
    }

    public boolean contains(VoteMessage message, String address) {
        String key = message.getTimes() + "_" + message.getTimes();
        PbftData pbftData = map.get(key);
        if (null == pbftData) {
            return false;
        }
        if (message.getStep() == 0) {
            return null != pbftData.getVote1ByAddress(address);
        } else if (message.getStep() == 1) {
            return null != pbftData.getVote2ByAddress(address);
        }
        return false;
    }

    public PbftData getCurrentResult(long height, int round) {
        String key = height + "_" + round;
        return map.get(key);
    }

    public void putBlockHeader(BlockHeader header) {
        Map<NulsHash, BlockHeader> map = headerMap.get(header.getHeight());
        if (null == map) {
            map = new HashMap<>();
            headerMap.put(header.getHeight(), map);
        }
        map.put(header.getHash(), header);
    }

    public BlockHeader getBlockHeader(long height, NulsHash hash) {
        Map<NulsHash, BlockHeader> map = headerMap.get(height);
        if (null == map) {
            return null;
        }
        return map.get(hash);
    }

    public void clearBlockHeader(long height) {
        headerMap.remove(height);
    }
}
