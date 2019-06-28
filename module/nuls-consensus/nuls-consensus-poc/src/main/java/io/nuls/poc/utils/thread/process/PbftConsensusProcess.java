package io.nuls.poc.utils.thread.process;

import io.nuls.base.RPCUtil;
import io.nuls.base.basic.AddressTool;
import io.nuls.base.basic.ProtocolVersion;
import io.nuls.base.data.Block;
import io.nuls.base.data.BlockExtendsData;
import io.nuls.base.data.BlockHeader;
import io.nuls.base.data.Transaction;
import io.nuls.base.protocol.ModuleHelper;
import io.nuls.core.core.ioc.SpringLiteContext;
import io.nuls.core.exception.NulsException;
import io.nuls.core.log.logback.NulsLogger;
import io.nuls.core.model.StringUtils;
import io.nuls.core.rpc.util.NulsDateUtils;
import io.nuls.poc.constant.ConsensusErrorCode;
import io.nuls.poc.model.bo.BlockData;
import io.nuls.poc.model.bo.Chain;
import io.nuls.poc.pbft.cache.BlockProuceKeyCache;
import io.nuls.poc.pbft.model.ProduceKey;
import io.nuls.poc.rpc.call.CallMethodUtils;
import io.nuls.poc.utils.enumeration.ConsensusStatus;
import io.nuls.poc.utils.manager.PbftConsensusManager;

import java.util.*;

/**
 * 共识处理器
 * Consensus processor
 *
 * @author tag
 * 2018/11/15
 */
public class PbftConsensusProcess implements IConsensusProcess {
    private NulsLogger consensusLogger;


    @Override
    public void process(Chain chain) {
        try {
            ProduceKey key = BlockProuceKeyCache.PRODUCE_KEYS_QUEUE.take();
            boolean canPackage = checkCanPackage(chain);
            if (!canPackage) {
                return;
            }
            consensusLogger = chain.getLogger();
            doWork(chain, key);
        } catch (Exception e) {
            chain.getLogger().error(e);
        }
    }

    /**
     * 检查节点打包状态
     * Check node packing status
     */
    private boolean checkCanPackage(Chain chain) throws Exception {
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


    private void doWork(Chain chain, ProduceKey key) throws Exception {
        /*
        检查节点状态
        Check node status
        */
        if (chain.getConsensusStatus().ordinal() < ConsensusStatus.RUNNING.ordinal()) {
            return;
        }

        long now = NulsDateUtils.getCurrentTimeSeconds();
        if (now >= key.getEndTime()) {
            return;
        }
        while (now < key.getStartTime()) {
            Thread.sleep(10L);
        }

        /*
        如果是共识节点则判断是否轮到自己出块
        1.节点是否正在打包
        2.当前时间是否处于节点打包开始时间和结束时间之间
        If it's a consensus node, it's time to decide whether it's your turn to come out of the block.
        1. Is the node packing?
        2. Is the current time between the start and end of the node packing?
        */
        try {
            packing(chain, key);
        } catch (Exception e) {
            consensusLogger.error(e);
        }
    }

    private void packing(Chain chain, ProduceKey key) throws Exception {
        /*
        等待出块
        Wait for blocks
        */
        long start = System.currentTimeMillis();
        Block block = doPacking(chain, key);
        consensusLogger.info("doPacking use:" + (System.currentTimeMillis() - start) + "ms" + "\n\n");

        /*
         * 打包完成之后，查看打包区块和主链最新区块是否连续，如果不连续表示打包过程中收到了上一个共识节点打包的区块，此时本地节点需要重新打包区块
         * After packaging, check whether the packaged block and the latest block in the main chain are continuous. If the block is not continuous,
         * the local node needs to repackage the block when it receives the packaged block from the previous consensus node in the packaging process.
         */
        if (null == block) {
            consensusLogger.error("make a null block");
            return;
        }
        try {
            CallMethodUtils.sendBlock(chain.getConfig().getChainId(), RPCUtil.encode(block.serialize()), key.getEndTime() - NulsDateUtils.getCurrentTimeSeconds());
        } catch (Exception e) {
            consensusLogger.error(e);
        }
    }

    public void fillProtocol(BlockExtendsData extendsData, int chainId) throws NulsException {
        if (ModuleHelper.isSupportProtocolUpdate()) {
            ProtocolVersion mainVersion = CallMethodUtils.getMainVersion(chainId);
            ProtocolVersion localVersion = CallMethodUtils.getLocalVersion(chainId);
            extendsData.setMainVersion(mainVersion.getVersion());
            extendsData.setBlockVersion(localVersion.getVersion());
            extendsData.setEffectiveRatio(localVersion.getEffectiveRatio());
            extendsData.setContinuousIntervalCount(localVersion.getContinuousIntervalCount());
        } else {
            extendsData.setMainVersion((short) 1);
            extendsData.setBlockVersion((short) 1);
            extendsData.setEffectiveRatio((byte) 80);
            extendsData.setContinuousIntervalCount((short) 100);
        }
    }

    @SuppressWarnings("unchecked")
    private Block doPacking(Chain chain, ProduceKey key) throws Exception {
        BlockHeader bestBlock = chain.getNewestHeader();
        long packageHeight = bestBlock.getHeight() + 1;
        BlockData bd = new BlockData();
        bd.setHeight(packageHeight);
        bd.setPreHash(bestBlock.getHash());
        bd.setTime(key.getEndTime());
        BlockExtendsData extendsData = new BlockExtendsData();
        extendsData.setRoundIndex(key.getRound().getIndex());
        extendsData.setConsensusMemberCount(key.getRound().getMemberCount());
        extendsData.setPackingIndexOfRound(key.getIndexOfRound());
        extendsData.setRoundStartTime(key.getRound().getStartTime());
        fillProtocol(extendsData, chain.getConfig().getChainId());

        Map<String, Object> resultMap = CallMethodUtils.getPackingTxList(chain, bd.getTime(), AddressTool.getStringAddressByBytes(key.getAddress()));
        List<Transaction> packingTxList = new ArrayList<>();

        /*
         * 检查组装交易过程中是否收到新区块
         * Verify that new blocks are received halfway through packaging
         * */
        bestBlock = chain.getNewestHeader();
        long realPackageHeight = bestBlock.getHeight() + 1;
        if (!(bd.getPreHash().equals(bestBlock.getHash()) && realPackageHeight > packageHeight)) {
            bd.setHeight(realPackageHeight);
            bd.setPreHash(bestBlock.getHash());
        }

        BlockExtendsData bestExtendsData = new BlockExtendsData(bestBlock.getExtend());
        boolean stateRootIsNull = false;
        if (resultMap == null) {
            extendsData.setStateRoot(bestExtendsData.getStateRoot());
            stateRootIsNull = true;
        } else {
            long txPackageHeight = Long.valueOf(resultMap.get("packageHeight").toString());
            String stateRoot = (String) resultMap.get("stateRoot");
            if (StringUtils.isBlank(stateRoot)) {
                extendsData.setStateRoot(bestExtendsData.getStateRoot());
                stateRootIsNull = true;
            } else {
                extendsData.setStateRoot(RPCUtil.decode(stateRoot));
            }
            if (realPackageHeight >= txPackageHeight) {
                List<String> txHexList = (List) resultMap.get("list");
                for (String txHex : txHexList) {
                    Transaction tx = new Transaction();
                    tx.parse(RPCUtil.decode(txHex), 0);
                    packingTxList.add(tx);
                }
            }
        }
        bd.setExtendsData(extendsData);
        /*
        组装系统交易（CoinBase/红牌/黄牌）+ 创建区块
        Assembly System Transactions (CoinBase/Red/Yellow)+ Create blocks
        */
        PbftConsensusManager consensusManager = SpringLiteContext.getBean(PbftConsensusManager.class);
        consensusManager.addConsensusTx(chain, bestBlock, packingTxList, key.getSelf(), key.getRound(), extendsData, bd.getTime());
        bd.setTxList(packingTxList);
        Block newBlock = consensusManager.createBlock(chain, bd, key.getAddress());
        /*
         * 验证打包中途是否收到新区块
         * Verify that new blocks are received halfway through packaging
         * */
        bestBlock = chain.getNewestHeader();
        if (!newBlock.getHeader().getPreHash().equals(bestBlock.getHash())) {
            newBlock.getHeader().setPreHash(bestBlock.getHash());
            newBlock.getHeader().setHeight(bestBlock.getHeight());
            if (stateRootIsNull) {
                bestExtendsData = new BlockExtendsData(bestBlock.getExtend());
                extendsData.setStateRoot(bestExtendsData.getStateRoot());
                newBlock.getHeader().setExtend(extendsData.serialize());
            }
        }
        consensusLogger.info("round index :" + key.getRound().getIndex());
        consensusLogger.info("make block height:" + newBlock.getHeader().getHeight() + ",txCount: " + newBlock.getTxs().size() + " , block size: " + newBlock.size() + " , time:" + NulsDateUtils.convertDate(new Date(newBlock.getHeader().getTime() * 1000)) + ",packEndTime:" +
                NulsDateUtils.convertDate(new Date(key.getEndTime() * 1000)) + ",hash:" + newBlock.getHeader().getHash().toHex() + ",preHash:" + newBlock.getHeader().getPreHash().toHex());
        return newBlock;
    }
}
