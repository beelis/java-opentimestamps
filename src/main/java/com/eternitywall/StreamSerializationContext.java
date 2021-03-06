package com.eternitywall;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.logging.Logger;

/**
 * Created by luca on 25/02/2017.
 */
public class StreamSerializationContext {


    private static Logger log = Logger.getLogger(StreamSerializationContext.class.getName());

    byte[] buffer = new byte[1024 * 4];
    int length = 0;

    public StreamSerializationContext() {
        this.buffer = new byte[1024 * 4];
        this.length = 0;
    }

    public byte[] getOutput() {
        return Arrays.copyOfRange(this.buffer, 0, this.length);
    }


    public void writeBool(boolean value) {
        if (value == true) {
            this.writeByte((byte) 0xff);
        } else {
            this.writeByte((byte) 0x00);
        }
    }

    public void writeVaruint(int value) {
        if (value == 0) {
            this.writeByte((byte) 0x00);
        } else {
            while (value != 0) {
                byte b = (byte) (value & 0b01111111);
                if (value > 0b01111111) {
                    b |= 0b10000000;
                }
                this.writeByte(b);
                if (value <= 0b01111111) {
                    break;
                }
                value >>= 7;
            }
        }
    }

    public void writeByte(byte value) {
        if (this.length >= this.buffer.length) {
            int newLenght = this.length * 2;
            byte[] swapBuffer = new byte[newLenght];
            swapBuffer = Arrays.copyOf(this.buffer, this.length);
            this.buffer = swapBuffer;
            this.length = newLenght;
        }

        this.buffer[this.length] = value;
        this.length++;
    }


    public void writeBytes(byte[] value) {
        for (int i = 0; i < value.length; i++) {
            this.writeByte(value[i]);
        }
    }

    public void writeVarbytes(byte[] value) {
        this.writeVaruint((byte) (value.length & (0xff)));
        this.writeBytes(value);
    }

    public String toString() {
        return Arrays.copyOf(this.buffer, this.length).toString();
    }

}
