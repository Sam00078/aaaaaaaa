package cn.justec.www.temperatureapp;

/**
 * Created by LEO LIN on 2015/12/29.
 */
public abstract class Checksum {
    protected byte[] m_buf;
    protected int m_len;
    protected int m_index;

    public Checksum(byte[] buf, int len, int index) {
        this.m_buf = buf;
        this.m_len = len;
        this.m_index = index;
    }

    /**
     * @param buf    �洢У��͵Ļ�������
     * @param len    У��͵��ֽ���
     * @param index  У����ڻ��������е�λ��
     * @return ״̬��Ϣ������0��ʾ�����Ч������������ֵ�����Ч
     */
    abstract int GetResult(byte[] buf, int len, int index);
}

class ArithmeticSum extends Checksum {
    public ArithmeticSum(byte[] buf, int len, int index) {
        super(buf, len, index);
    }

    @Override
    int GetResult(byte[] buf, int len, int index) {

        int result = 0;

        int checksum = 0;

        for (int i = this.m_index; i < this.m_len; i++) {
            checksum += (this.m_buf[i] & 0xff);
        }

        switch (len) {
            case 1:
                buf[index] = (byte) (checksum & 0xff);
                break;

            case 2:
                buf[index + 1] = (byte) (checksum & 0xff);

                buf[index] = (byte) ((checksum >> 8) & 0xff);
                break;

            case 4:
                buf[index + 3] = (byte) (checksum & 0xff);

                buf[index + 2] = (byte) ((checksum >> 8) & 0xff);

                buf[index + 1] = (byte) ((checksum >> 16) & 0xff);

                buf[index] = (byte) ((checksum >> 24) & 0xff);
                break;

            default:
                result = -1;
                break;
        }

        return result;
    }
}

class JusteCSum extends Checksum {
    public JusteCSum(byte[] buf, int len, int index) {
        super(buf, len, index);
    }

    private static final int wCRCTableAbs[] = {
            0x0000, 0xCC01, 0xD801, 0x1400, 0xF001, 0x3C00, 0x2800, 0xE401,
            0xA001, 0x6C00, 0x7800, 0xB401, 0x5000, 0x9C01, 0x8801, 0x4400,
    };

    @Override
    int GetResult(byte[] buf, int len, int index) {
        int result = 0;

        if (2 == len) {
            int wCRC = 0xFFFF;

            int chChar;

            for (int i = this.m_index; i < this.m_len; i ++)
            {
                chChar = this.m_buf[i] & 0xff;
                wCRC = wCRCTableAbs[(chChar ^ wCRC) & 15] ^ (wCRC >> 4);
                wCRC = wCRCTableAbs[((chChar >> 4) ^ wCRC) & 15] ^ (wCRC >> 4);
            }

            buf[index + 1] = (byte) (wCRC & 0xff);

            buf[index] = (byte) (wCRC >> 8);
        } else {
            result = -1;
        }

        return result;
    }
}

class Factory {
    public final static String CHECKSUM_ARITHMETIC = "cn.justec.www.checksum.arithmetic";
    public final static String CHECKSUM_JUSTEC = "cn.justec.www.checksum.justec";

    private byte[] buf;
    private int len;
    private int index;

    public Factory(byte[] buf, int len, int index) {
        this.buf = buf;
        this.len = len;
        this.index = index;
    }

    /**
     * @param n У��͵�����
     * @return ���ش����õ�У��Ͷ��� ���� null
     * @see Factory#CHECKSUM_ARITHMETIC
     * @see Factory#CHECKSUM_JUSTEC
     */
    public Checksum CreateChecksum(String n) {
        Checksum checksum;

        switch (n) {
            case CHECKSUM_ARITHMETIC:
                checksum = new ArithmeticSum(this.buf, this.len, this.index);
                break;

            case CHECKSUM_JUSTEC:
                checksum = new JusteCSum(this.buf, this.len, this.index);
                break;

            default:
                checksum = null;
                break;
        }

        return checksum;
    }
}
