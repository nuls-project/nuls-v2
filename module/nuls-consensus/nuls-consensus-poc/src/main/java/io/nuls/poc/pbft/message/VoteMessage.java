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

    private long round;

    private byte step = 0;
    /**
     * 精确到秒
     */
    private long startTime;

    private NulsHash blockHash;

    //恶意分叉时，传递证据。
    private BlockHeader header1;
    private BlockHeader header2;

    private byte[] sign;
    private String address;

    @Override
    protected void serializeToStream(NulsOutputStreamBuffer stream) throws IOException {
        stream.writeInt64(height);
        stream.writeUint32(round);
        stream.write(step);
        stream.write(blockHash.getBytes());
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
                buffer.writeUint32(round);
                buffer.write(step);
                buffer.write(blockHash.getBytes());
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
        this.round = byteBuffer.readUint32();
        this.step = byteBuffer.readByte();
        this.blockHash = byteBuffer.readHash();
        this.header1 = byteBuffer.readNulsData(new BlockHeader());
        this.header2 = byteBuffer.readNulsData(new BlockHeader());
        this.sign = byteBuffer.readByLengthByte();

    }

    @Override
    public int size() {
        int size = 13;
        size += 32;
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

    public long getRound() {
        return round;
    }

    public void setRound(long round) {
        this.round = round;
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

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

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
