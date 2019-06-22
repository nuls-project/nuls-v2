package io.nuls.poc.pbft.model;

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

    private List<VoteData> voteDataList1 = new ArrayList<>();
    private List<VoteData> voteDataList2 = new ArrayList<>();

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
        this.voteDataList1.add(data);
        Integer count = map1.get(data.getHash());
        if (null == count) {
            count = 0;
        }
        map1.put(data.getHash(), count + 1);
    }

    public void addVote2Result(VoteData data) {
        this.voteDataList2.add(data);
        Integer count = map2.get(data.getHash());
        if (null == count) {
            count = 0;
        }
        map2.put(data.getHash(), count + 1);
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

    public boolean hasVoted1(byte[] address) {
        for (VoteData vote : this.voteDataList1) {
            if (ArraysTool.arrayEquals(vote.getAddress(), address)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasVoted2(byte[] address) {
        for (VoteData vote : this.voteDataList2) {
            if (ArraysTool.arrayEquals(vote.getAddress(), address)) {
                return true;
            }
        }
        return false;
    }

    public VoteData getVote1ByAddress(byte[] address) {
        return getVoteByAddress(address, this.voteDataList1);
    }

    public VoteData getVote2ByAddress(byte[] address) {
        return getVoteByAddress(address, this.voteDataList2);
    }

    public VoteData getVoteByAddress(byte[] address, List<VoteData> list) {
        for (VoteData data : list) {
            if (ArraysTool.arrayEquals(address, data.getAddress())) {
                return data;
            }
        }
        return null;
    }
}
