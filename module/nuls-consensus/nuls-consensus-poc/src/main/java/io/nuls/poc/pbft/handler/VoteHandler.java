package io.nuls.poc.pbft.handler;

import io.nuls.base.RPCUtil;
import io.nuls.base.basic.AddressTool;
import io.nuls.base.protocol.MessageProcessor;
import io.nuls.base.signture.BlockSignature;
import io.nuls.core.core.annotation.Component;
import io.nuls.core.exception.NulsException;
import io.nuls.poc.pbft.BlockVoter;
import io.nuls.poc.pbft.manager.VoterManager;
import io.nuls.poc.pbft.message.VoteMessage;
import io.nuls.poc.utils.LoggerUtil;

/**
 * @author Niels
 */
@Component("VoteHandlerV1")
public class VoteHandler implements MessageProcessor {
    @Override
    public String getCmd() {
        return "vote";
    }

    @Override
    public void process(int chainId, String nodeId, String msg) {
        VoteMessage message = new VoteMessage();
        try {
            message.parse(RPCUtil.decode(msg), 0);
        } catch (NulsException e) {
            LoggerUtil.commonLog.error(e);
            return;
        }

        // 验证签名
        BlockSignature signature = new BlockSignature();
        try {
            signature.parse(message.getSign(), 0);
        } catch (NulsException e) {
            LoggerUtil.commonLog.error(e);
            return;
        }
        if (signature.verifySignature(message.getHash()).isFailed()) {
            LoggerUtil.commonLog.warn("discard wrong vote.");
            return;
        }

        BlockVoter voter = VoterManager.getVoter(chainId);
        voter.recieveVote(nodeId, message, AddressTool.getStringAddressByBytes(AddressTool.getAddress(signature.getPublicKey(), chainId)));
    }
}
