package io.nuls.poc.pbft.message;

import io.nuls.base.basic.AddressTool;
import io.nuls.base.basic.NulsByteBuffer;
import io.nuls.base.basic.NulsOutputStreamBuffer;
import io.nuls.base.data.BaseBusinessMessage;
import io.nuls.base.data.BlockHeader;
import io.nuls.base.data.NulsHash;
import io.nuls.base.signture.BlockSignature;
import io.nuls.core.constant.ToolsConstant;
import io.nuls.core.crypto.UnsafeByteArrayOutputStream;
import io.nuls.core.exception.NulsException;
import io.nuls.core.parse.SerializeUtils;
import io.nuls.poc.utils.LoggerUtil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * @author Niels
 */
public class VoteMessage extends BaseBusinessMessage {

    private long height;

    private long times;

    private byte step = 0;

    private NulsHash blockHash;

    private byte[] sign;
//    /**
//     * 投空块时需要
//     */
//    private long roundIndex;
//    /**
//     * 投空块时需要
//     * 精确到秒
//     */
//    private long roundStartTime;
//    /**
//     * 投空块时需要
//     */
//    private int currentMemberIndex;

    /**
     * 恶意分叉时，传递证据。
     */
    private BlockHeader header1;
    private BlockHeader header2;


    /**
     * 非序列化字段
     */
    private String address;

    @Override
    protected void serializeToStream(NulsOutputStreamBuffer stream) throws IOException {
        stream.writeInt64(height);
        stream.writeUint32(times);
        stream.write(step);
        stream.write(blockHash.getBytes());
//        stream.writeInt64(roundIndex);
//        stream.writeUint32(roundStartTime);
//        stream.writeUint32(currentMemberIndex);
        stream.writeNulsData(header1);
        stream.writeNulsData(header2);
        stream.writeBytesWithLength(sign);
    }

    public byte[] serializeForDigest() throws IOException {
        ByteArrayOutputStream bos = null;
        try {
            int size = size() - SerializeUtils.sizeOfBytes(sign);
            bos = new UnsafeByteArrayOutputStream(size);
            NulsOutputStreamBuffer buffer = new NulsOutputStreamBuffer(bos);
            if (size == 0) {
                bos.write(ToolsConstant.PLACE_HOLDER);
            } else {
                buffer.writeInt64(height);
                buffer.writeUint32(times);
                buffer.write(step);
                buffer.write(blockHash.getBytes());
//                buffer.writeInt64(roundIndex);
//                buffer.writeUint32(roundStartTime);
//                buffer.writeUint32(currentMemberIndex);
                buffer.writeNulsData(header1);
                buffer.writeNulsData(header2);
            }
            return bos.toByteArray();
        } finally {
            if (bos != null) {
                try {
                    bos.close();
                } catch (IOException e) {
                    throw e;
                }
            }
        }
    }

    @Override
    public void parse(NulsByteBuffer byteBuffer) throws NulsException {
        this.height = byteBuffer.readInt64();
        this.times = byteBuffer.readUint32();
        this.step = byteBuffer.readByte();
        this.blockHash = byteBuffer.readHash();
//        this.roundIndex = byteBuffer.readInt64();
//        this.roundStartTime = byteBuffer.readUint32();
//        this.currentMemberIndex = (int) byteBuffer.readUint32();
        this.header1 = byteBuffer.readNulsData(new BlockHeader());
        this.header2 = byteBuffer.readNulsData(new BlockHeader());
        this.sign = byteBuffer.readByLengthByte();

    }

    @Override
    public int size() {
        int size = 13;
        size += 32;
        size += SerializeUtils.sizeOfInt64();
        size += SerializeUtils.sizeOfUint32();
        size += SerializeUtils.sizeOfUint32();
        size += SerializeUtils.sizeOfNulsData(header1);
        size += SerializeUtils.sizeOfNulsData(header2);
        size += SerializeUtils.sizeOfBytes(sign);
        return size;
    }

    public byte getStep() {
        return step;
    }

    public void setStep(byte step) {
        this.step = step;
    }

    public long getHeight() {
        return height;
    }

    public void setHeight(long height) {
        this.height = height;
    }

    public long getTimes() {
        return times;
    }

    public void setTimes(long times) {
        this.times = times;
    }

    public NulsHash getBlockHash() {
        return blockHash;
    }

    public void setBlockHash(NulsHash blockHash) {
        this.blockHash = blockHash;
    }

    public byte[] getSign() {
        return sign;
    }

    public void setSign(byte[] sign) {
        this.sign = sign;
    }

//    public long getRoundIndex() {
//        return roundIndex;
//    }
//
//    public void setRoundIndex(long roundIndex) {
//        this.roundIndex = roundIndex;
//    }
//
//    public long getRoundStartTime() {
//        return roundStartTime;
//    }
//
//    public void setRoundStartTime(long roundStartTime) {
//        this.roundStartTime = roundStartTime;
//    }
//
//    public int getCurrentMemberIndex() {
//        return currentMemberIndex;
//    }
//
//    public void setCurrentMemberIndex(int currentMemberIndex) {
//        this.currentMemberIndex = currentMemberIndex;
//    }

    public BlockHeader getHeader1() {
        return header1;
    }

    public void setHeader1(BlockHeader header1) {
        this.header1 = header1;
    }

    public BlockHeader getHeader2() {
        return header2;
    }

    public void setHeader2(BlockHeader header2) {
        this.header2 = header2;
    }

    public String getAddress(int chainId) {
        if (null == address && this.sign != null) {
            BlockSignature bs = new BlockSignature();
            try {
                bs.parse(this.sign, 0);
            } catch (NulsException e) {
                LoggerUtil.commonLog.error(e);
            }
            this.address = AddressTool.getStringAddressByBytes(AddressTool.getAddress(bs.getPublicKey(), chainId));
        }
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }
}
