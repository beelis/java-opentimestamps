package com.eternitywall;

import java.util.logging.Logger;

/**
 * Prepend a prefix to a message.
 *
 * @extends com.eternitywall.OpBinary
 */
class OpPrepend extends OpBinary {


    private static Logger log = Logger.getLogger(OpPrepend.class.getName());


    byte[] arg;

    public static byte _TAG = (byte) 0xf1;

    @Override
    public byte _TAG() {
        return OpPrepend._TAG;
    }

    @Override
    public String _TAG_NAME() {
        return "prepend";
    }

    OpPrepend() {
        super();
        this.arg = new byte[]{};
    }

    OpPrepend(byte[] arg_) {
        super(arg_);
        this.arg = arg_;
    }

    @Override
    public byte[] call(byte[] msg) {
        return Utils.arraysConcat(this.arg, msg);
    }

    public static Op deserializeFromTag(StreamDeserializationContext ctx, byte tag) {
        return OpBinary.deserializeFromTag(ctx, tag);
    }
}