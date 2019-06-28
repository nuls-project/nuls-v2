package io.nuls.block.thread;

import io.nuls.base.data.Block;
import io.nuls.base.data.NulsHash;
import io.nuls.block.manager.ContextManager;
import io.nuls.block.model.BlockData;
import io.nuls.block.model.BlockSure;
import io.nuls.block.model.Chain;
import io.nuls.block.model.ChainContext;
import io.nuls.block.service.BlockService;
import io.nuls.block.utils.LoggerUtil;
import io.nuls.core.core.ioc.SpringLiteContext;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Niels
 */
public class BlockSaver implements Runnable {
    private static Map<Long, Map<NulsHash, BlockData>> blockMap = new HashMap<>();
    private static LinkedBlockingQueue<BlockSure> sureQueue = new LinkedBlockingQueue<>(10);
    private final int chainId;

    private BlockService blockService = SpringLiteContext.getBean(BlockService.class);


    public void start() {
        new Thread(this).start();
    }

    public BlockSaver(int chainId) {
        this.chainId = chainId;
    }

    @Override
    public void run() {
        while (true) {
            try {
                BlockSure sure = sureQueue.take();
                NulsHash hash = sure.getHash();
                if (null == hash || hash.equals(NulsHash.EMPTY_NULS_HASH)) {
                    continue;
                }
                ChainContext context = ContextManager.getContext(chainId);
                long bestHeight = context.getLatestHeight();
                if (sure.getHeight() <= bestHeight) {
                    continue;
                }
                Map<NulsHash, BlockData> blocksMap = blockMap.get(sure.getHeight());
                while (null == blocksMap) {
                    Thread.sleep(100L);
                    blocksMap = blockMap.get(sure.getHeight());
                }
                BlockData blockData = blocksMap.get(hash);
                if (blockData == null) {
                    Thread.sleep(100L);
                    blockData = blocksMap.get(hash);
                }

                if (blockData.getBlock().getHeader().getHash().equals(sure.getHash())) {
                    blockService.sureBlock(blockData.getBlock(), this.chainId, false, blockData.getContractList(), 1, false, false);
                    blockMap.remove(sure.getHeight());
                }
            } catch (Exception e) {
                LoggerUtil.COMMON_LOG.error(e);
            }
        }
    }

    public void addBlock(Block block, List contractList) {
        Map<NulsHash, BlockData> blocksMap = blockMap.get(block.getHeader().getHeight());
        if (null == blocksMap) {
            blocksMap = new HashMap<>();
            blockMap.put(block.getHeader().getHeight(), blocksMap);
        }
        blocksMap.put(block.getHeader().getHash(), new BlockData(block, contractList));
    }

    public void addSure(BlockSure sure) {
        sureQueue.offer(sure);
    }

    public Block getBlock(NulsHash hash) {
        BlockData blockData = null;
        for (Map<NulsHash, BlockData> map : blockMap.values()) {
            blockData = map.get(hash);
            if (null != blockData) {
                break;
            }
        }
        return blockData.getBlock();
    }
}
