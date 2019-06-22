package io.nuls.poc.pbft.cache;

import io.nuls.base.data.BlockHeader;
import io.nuls.base.data.NulsHash;

/**
 * @author Niels
 */
public class PreCommitCache {

    public static final PreCommitCache INSTANCE = new PreCommitCache();

    private PreCommitCache() {
    }

    public static PreCommitCache getInstance() {
        return INSTANCE;
    }

    private long height;

    private int round;

    //下一次可以投票的hash
    private NulsHash shouldNext;

    private BlockHeader header;

    private BlockHeader forkHeader;

    public void change(long height, int round, NulsHash hash, BlockHeader header, BlockHeader forkHeader) {
        this.height = height;
        this.round = round;
        this.shouldNext = hash;
        this.header = header;
        this.forkHeader = forkHeader;
    }

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

    public BlockHeader getHeader() {
        return header;
    }

    public void setHeader(BlockHeader header) {
        this.header = header;
    }

    public BlockHeader getForkHeader() {
        return forkHeader;
    }

    public void setForkHeader(BlockHeader forkHeader) {
        this.forkHeader = forkHeader;
    }

    public NulsHash getShouldNext() {
        return shouldNext;
    }

    public void setShouldNext(NulsHash shouldNext) {
        this.shouldNext = shouldNext;
    }

    public void clear(NulsHash hash) {
        if (null != hash) {
            this.height = 0;
            this.round = 0;
            this.header = null;
            this.forkHeader = null;
        }
    }
}
