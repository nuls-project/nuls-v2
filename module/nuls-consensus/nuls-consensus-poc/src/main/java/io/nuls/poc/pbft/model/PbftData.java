package io.nuls.poc.pbft.model;

import io.nuls.base.basic.AddressTool;
import io.nuls.base.data.NulsHash;
import io.nuls.core.model.ArraysTool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Niels
 */
public class PbftData {

    private long height;

    private int round;

    private long startTime;

    private long endTime;

    private Map<String, VoteData> voteDataList1 = new HashMap<>();
    private Map<String, VoteData> voteDataList2 = new HashMap<>();

    private Map<NulsHash, Integer> map1 = new HashMap<>();
    private Map<NulsHash, Integer> map2 = new HashMap<>();

    public long getHeight() {
        return height;
    }

    public void setHeight(long height) {
        this.height = height;
    }

    public int getRound() {
        return round;
    }

    public void setRound(int round) {
        this.round = round;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public long getEndTime() {
        return endTime;
    }

    public void setEndTime(long endTime) {
        this.endTime = endTime;
    }

    public void addVote1Result(VoteData data) {
        this.addVote(this.voteDataList1, map1, data);
    }

    public void addVote2Result(VoteData data) {
        this.addVote(this.voteDataList2, map2, data);
    }

    private void addVote(Map<String, VoteData> dataMap, Map<NulsHash, Integer> resultMap, VoteData data) {
        VoteData oldData = dataMap.get(data.getAddress());
        dataMap.put(data.getAddress(), data);

        if (null != oldData && oldData.getHash() == data.getHash()) {
            return;
        }
        if (null != oldData && oldData.getHash() != null && oldData.getHash().equals(data.getHash())) {
            return;
        }
        Integer count = resultMap.get(data.getHash());
        if (null == count) {
            count = 0;
        }
        resultMap.put(data.getHash(), count + 1);
        if (null == oldData) {
            return;
        }
        count = resultMap.get(oldData.getHash());
        if (null == count) {
            count = 1;
        }
        resultMap.put(oldData.getHash(), count - 1);
    }

    public Map<NulsHash, Integer> getVote1Result() {
        return this.map1;
    }

    public VoteResultItem getVote1LargestItem() {
        VoteResultItem item = new VoteResultItem();
        int max = 0;
        NulsHash hash = null;
        for (Map.Entry<NulsHash, Integer> entry : this.map1.entrySet()) {
            Integer val = entry.getValue();
            if (val > max) {
                max = val;
                hash = entry.getKey();
            }
        }
        item.setCount(max);
        item.setHash(hash);
        return item;
    }

    public VoteResultItem getVote2LargestItem() {
        VoteResultItem item = new VoteResultItem();
        int max = 0;
        NulsHash hash = null;
        for (Map.Entry<NulsHash, Integer> entry : this.map2.entrySet()) {
            Integer val = entry.getValue();
            if (val > max) {
                max = val;
                hash = entry.getKey();
            }
        }
        item.setCount(max);
        item.setHash(hash);
        return item;
    }

    public VoteData hasVoted1(byte[] address) {
        for (VoteData vote : this.voteDataList1.values()) {
            if (vote.getAddress().equals(AddressTool.getStringAddressByBytes(address))) {
                return vote;
            }
        }
        return null;
    }

    public VoteData hasVoted2(byte[] address) {
        for (VoteData vote : this.voteDataList2.values()) {
            if (vote.getAddress().equals(AddressTool.getStringAddressByBytes(address))) {
                return vote;
            }
        }
        return null;
    }

    public VoteData getVote1ByAddress(String address) {
        return this.voteDataList1.get(address);
    }

    public VoteData getVote2ByAddress(String address) {
        return this.voteDataList2.get(address);
    }

}
