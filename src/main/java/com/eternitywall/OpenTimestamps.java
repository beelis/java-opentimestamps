package com.eternitywall;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.logging.Logger;

/**
 * com.eternitywall.OpenTimestamps module.
 *
 * @author EternityWall
 * @module com.eternitywall.OpenTimestamps
 * @license LPGL3
 */

public class OpenTimestamps {


    private static Logger log = Logger.getLogger(OpenTimestamps.class.getName());

    /**
     * Show information on a timestamp.
     *
     * @param {byte[]} ots - The ots array buffer.
     * @exports com.eternitywall.OpenTimestamps/info
     */
    public static String info(byte[] ots) {
        if (ots == null) {
            return "No ots file";
        }

        StreamDeserializationContext ctx = new StreamDeserializationContext(ots);
        DetachedTimestampFile detachedTimestampFile = DetachedTimestampFile.deserialize(ctx);

        String fileHash = Utils.bytesToHex(detachedTimestampFile.timestamp.msg);
        String hashOp = ((OpCrypto) detachedTimestampFile.fileHashOp)._TAG_NAME();

        String firstLine = "File " + hashOp + " hash: " + fileHash + '\n';
        return firstLine + "Timestamp:\n" + detachedTimestampFile.timestamp.strTree(0);
    }

    /**
     * Create timestamp with the aid of a remote calendar. May be specified multiple times.
     *
     * @param {File} file - The plain File to stamp.
     * @return {byte[]} The plain array buffer of stamped.
     */
    public static byte[] stamp(InputStream inputStream) throws IOException {
        // Read from file reader stream
        try {
            DetachedTimestampFile fileTimestamp;
            fileTimestamp = DetachedTimestampFile.from(new OpSHA256(), inputStream);
            return stamp(fileTimestamp);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            log.severe("Invalid InputStream");
            throw new IOException();
        }
    }

    /**
     * Create timestamp with the aid of a remote calendar. May be specified multiple times.
     *
     * @param {byte[]}  hash - The sha 256 of the file to stamp.
     * @return {byte[]} The plain array buffer of stamped.
     */
    public static byte[] stamp(byte[] hash) throws IOException {
        // Read from file reader stream
        DetachedTimestampFile fileTimestamp;
        fileTimestamp = DetachedTimestampFile.from(new OpSHA256(), hash);
        return stamp(fileTimestamp);
    }

        /**
         * Create timestamp with the aid of a remote calendar. May be specified multiple times.
         *
         * @param {DetachedTimestampFile}  fileTimestamp - The timestamp to stamp.
         * @return {byte[]} The plain array buffer of stamped.
         */
    private static byte[] stamp(DetachedTimestampFile fileTimestamp) throws IOException {

         /* Add nonce:
       * Remember that the files - and their timestamps - might get separated
       * later, so if we didn't use a nonce for every file, the timestamp
       * would leak information on the digests of adjacent files.
       * */
        Timestamp merkleRoot;
        byte[] bytesRandom16 = new byte[16];
        try {
            bytesRandom16 = Utils.randBytes(16);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            throw new IOException();
        }

        // nonce_appended_stamp = file_timestamp.timestamp.ops.add(com.eternitywall.OpAppend(os.urandom(16)))
        Op opAppend = new OpAppend(bytesRandom16);
        Timestamp nonceAppendedStamp = fileTimestamp.timestamp.ops.get(opAppend);
        if (nonceAppendedStamp == null) {
            nonceAppendedStamp = new Timestamp(opAppend.call(fileTimestamp.timestamp.msg));
            fileTimestamp.timestamp.ops.put(opAppend, nonceAppendedStamp);
        }

        // merkle_root = nonce_appended_stamp.ops.add(com.eternitywall.OpSHA256())
        Op opSHA256 = new OpSHA256();
        merkleRoot = nonceAppendedStamp.ops.get(opSHA256);
        if (merkleRoot == null) {
            merkleRoot = new Timestamp(opSHA256.call(nonceAppendedStamp.msg));
            nonceAppendedStamp.ops.put(opSHA256, merkleRoot);
        }

        Timestamp merkleTip = merkleRoot;
        List<String> calendarUrls = new ArrayList<String>();
        calendarUrls.add("https://alice.btc.calendar.opentimestamps.org");
        // calendarUrls.append('https://b.pool.opentimestamps.org');
        calendarUrls.add("https://ots.eternitywall.it");

        Timestamp resultTimestamp = OpenTimestamps.createTimestamp(merkleTip, calendarUrls);

        if (resultTimestamp == null) {
            throw new IOException();
        }
        // com.eternitywall.Timestamp serialization
        StreamSerializationContext css = new StreamSerializationContext();
        fileTimestamp.serialize(css);
        return css.getOutput();
    }

    /**
     * Create a timestamp
     *
     * @param {Timestamp}    timestamp - The timestamp.
     * @param {List<String>} calendarUrls - List of calendar's to use.
     * @return {Timestamp} The created timestamp.
     */
    private static Timestamp createTimestamp(Timestamp timestamp, List<String> calendarUrls) {
        List<Calendar> calendars = new ArrayList<Calendar>();
        /*for (final String calendarUrl : calendarUrls) {
            com.eternitywall.Calendar calendar = new com.eternitywall.Calendar(calendarUrl);
            calendars.add(calendar.submit(timestamp.msg));
        }*/
        Calendar calendar = new Calendar(calendarUrls.get(0));
        Timestamp resultTimestamp = calendar.submit(timestamp.msg);
        timestamp.merge(resultTimestamp);
        return timestamp;
    }



    /**
     * Verify a timestamp.
     *  @param {byte[]}  ots - The ots array buffer containing the proof to verify.
     * @param {byte[]}  stamped - The plain array buffer to verify.
     */
    public static Long verify(byte[] ots, byte[] stamped) throws IOException {
        // Read OTS
        DetachedTimestampFile detachedTimestamp = null;
        try {
            StreamDeserializationContext ctx = new StreamDeserializationContext(ots);
            detachedTimestamp = DetachedTimestampFile.deserialize(ctx);
        } catch (Exception e) {
            System.err.print("com.eternitywall.StreamDeserializationContext error");
        }

        // Read STAMPED
        byte[] actualFileDigest = new byte[0];
        try {
            actualFileDigest = stamped.clone();
        } catch (Exception e) {
            log.severe("com.eternitywall.StreamDeserializationContext : file hash error");
        }

        // Call Verify
        return OpenTimestamps.verify(detachedTimestamp,actualFileDigest);
    }

    /**
     * Verify a timestamp.
     *  @param {File}  ots - The ots array buffer containing the proof to verify.
     * @param {InputStream}  inputStream - The input stream to verify.
     */
    public static Long verify(byte[] ots, InputStream inputStream) throws IOException {

        // Read OTS
        DetachedTimestampFile detachedTimestamp = null;
        try {
            StreamDeserializationContext ctx = new StreamDeserializationContext(ots);
            detachedTimestamp = DetachedTimestampFile.deserialize(ctx);
        } catch (Exception e) {
            System.err.print("com.eternitywall.StreamDeserializationContext error");
        }

        // Read STAMPED
        byte[] actualFileDigest = new byte[0];
        try {
            actualFileDigest = ((OpCrypto) (detachedTimestamp.fileHashOp)).hashFd(inputStream);
        } catch (Exception e) {
            log.severe("com.eternitywall.StreamDeserializationContext : inputStream error");
        }

        // Call Verify
        return OpenTimestamps.verify(detachedTimestamp,actualFileDigest);
    }

    /**
     * Verify a timestamp.
     *  @param {File}  ots - The ots array buffer containing the proof to verify.
     * @param {File}  stamped - The File to verify.
     */
    public static Long verify(byte[] ots, File stamped) throws IOException {

        // Read OTS
        DetachedTimestampFile detachedTimestamp = null;
        try {
            StreamDeserializationContext ctx = new StreamDeserializationContext(ots);
            detachedTimestamp = DetachedTimestampFile.deserialize(ctx);
        } catch (Exception e) {
            System.err.print("com.eternitywall.StreamDeserializationContext error");
        }

        // Read STAMPED
        byte[] actualFileDigest = new byte[0];
        try {
            actualFileDigest = ((OpCrypto) (detachedTimestamp.fileHashOp)).hashFd(stamped);
        } catch (Exception e) {
            log.severe("com.eternitywall.StreamDeserializationContext : file stream error");
        }

        // Call Verify
        return OpenTimestamps.verify(detachedTimestamp,actualFileDigest);
    }



    /**
     * Verify a timestamp.
     *
     * @param {DetachedTimestampFile}  detachedTimestamp - The ots containing the proof to verify.
     * @param {byte[]}  actualFileDigest - The plain array buffer stamped.
     */
    private static Long verify(DetachedTimestampFile detachedTimestamp, byte[] actualFileDigest) {

        byte[] detachedFileDigest = detachedTimestamp.fileDigest();
        if (!Arrays.equals(actualFileDigest, detachedFileDigest)) {
            log.severe("Expected digest " + Utils.bytesToHex(detachedTimestamp.fileDigest()));
            log.severe("File does not match original!");

        }

        // console.log(com.eternitywall.Timestamp.strTreeExtended(detachedTimestamp.timestamp, 0));
        return OpenTimestamps.verifyTimestamp(detachedTimestamp.timestamp);
    }

    /**
     * Verify a timestamp.
     *
     * @param {com.eternitywall.Timestamp} timestamp - The timestamp.
     * @return {int} unix timestamp if verified, undefined otherwise.
     */
    private static Long verifyTimestamp(Timestamp timestamp) {
        Boolean found = false;

        for (Map.Entry<byte[], TimeAttestation> item : timestamp.allAttestations().entrySet()) {
            byte[] msg = item.getKey();
            TimeAttestation attestation = item.getValue();

            if (!found) { // Verify only the first com.eternitywall.BitcoinBlockHeaderAttestation
                if (attestation instanceof PendingAttestation) {
                    // console.log('com.eternitywall.PendingAttestation: pass ');
                } else if (attestation instanceof BitcoinBlockHeaderAttestation) {
                    found = true;
                    // console.log('Request to insight ');
                    Insight insight = new Insight("https://insight.bitpay.com/api");

                    String height = String.valueOf(((BitcoinBlockHeaderAttestation) attestation).height);
                    InsightResponse blockHash = insight.blockhash(height);
                    InsightResponse blockInfo = insight.block(blockHash.getBlockHash());

                    byte[] merkle = Utils.hexToBytes(blockInfo.getMerkleroot());
                    byte[] message = Utils.arrayReverse(msg);

                    // console.log('merkleroot: ' + com.eternitywall.Utils.bytesToHex(merkle));
                    // console.log('msg: ' + com.eternitywall.Utils.bytesToHex(message));
                    // console.log('Time: ' + (new Date(blockInfo.time * 1000)));

                    // One Bitcoin attestation is enought
                    if (Arrays.equals(merkle, message)) {
                        return blockInfo.getTime();
                    } else {
                        return null;
                    }
                }
            }
        }
        if (!found) {
            return null;
        }
        return null;
    }

    /**
     * Upgrade a timestamp.
     *
     * @param {byte[]} ots - The ots array buffer containing the proof to verify.
     */
    public static byte[] upgrade(byte[] ots) {

        // Read OTS
        DetachedTimestampFile detachedTimestamp = null;
        try {
            StreamDeserializationContext ctx = new StreamDeserializationContext(ots);
            detachedTimestamp = DetachedTimestampFile.deserialize(ctx);
        } catch (Exception e) {
            log.severe("com.eternitywall.StreamDeserializationContext error");
        }

        // Upgrade timestamp
        boolean changed = OpenTimestamps.upgradeTimestamp(detachedTimestamp.timestamp);

        if (changed) {
            log.info("Timestamp upgraded");
        }

        if (detachedTimestamp.timestamp.isTimestampComplete()) {
            log.info("Timestamp complete");
        } else {
            log.info("Timestamp not complete");
        }


        StreamSerializationContext css = new StreamSerializationContext();
        detachedTimestamp.serialize(css);
        return css.getOutput();
    }


    /**
     * Attempt to upgrade an incomplete timestamp to make it verifiable.
     * Note that this means if the timestamp that is already complete, False will be returned as nothing has changed.
     *
     * @param {Timestamp} timestamp - The timestamp.
     */
    private static boolean upgradeTimestamp(Timestamp timestamp) {
        // Check remote calendars for upgrades.
        // This time we only check PendingAttestations - we can't be as agressive.

        List<String> calendarUrls = new ArrayList<String>();
        calendarUrls.add("https://alice.btc.calendar.opentimestamps.org");
        // calendarUrls.append('https://b.pool.opentimestamps.org');
        calendarUrls.add("https://ots.eternitywall.it");

        boolean upgraded = false;

        Set<TimeAttestation> existingAttestations = timestamp.getAttestations();
        for (Timestamp subStamp : timestamp.directlyVerified()) {
            for (TimeAttestation attestation : subStamp.attestations) {
                if (attestation instanceof PendingAttestation) {
                    String calendarUrl = new String(((PendingAttestation) attestation).uri, StandardCharsets.UTF_8);
                    // var calendarUrl = calendarUrls[0];
                    byte[] commitment = subStamp.msg;

                    Calendar calendar = new Calendar(calendarUrl);
                    Timestamp upgradedStamp = OpenTimestamps.upgradeStamp(subStamp, calendar, commitment, existingAttestations);
                    subStamp.merge(upgradedStamp);
                    upgraded = true;
                }
            }
        }
        return upgraded;
    }

    private static Timestamp upgradeStamp(Timestamp subStamp, Calendar calendar, byte[] commitment, Set<TimeAttestation> existingAttestations) {
        Timestamp upgradedStamp = calendar.getTimestamp(commitment);
        Set<TimeAttestation> attsFromRemote = upgradedStamp.getAttestations();
        if (attsFromRemote.size() > 0) {
            // log.info(attsFromRemote.size + ' attestation(s) from ' + calendar.url);
        }

        // Set difference from remote attestations & existing attestations
        Set<TimeAttestation> newAttestations = attsFromRemote;
        newAttestations.removeAll(existingAttestations);

        if (newAttestations.size() > 0) {
            // changed & found_new_attestations
            // foundNewAttestations = true;
            // log.info(attsFromRemote.size + ' attestation(s) from ' + calendar.url);

            // Set union of existingAttestations & newAttestations
            existingAttestations.addAll(newAttestations);

            return upgradedStamp;
            // subStamp.merge(upgradedStamp);
            // args.cache.merge(upgraded_stamp)
            // sub_stamp.merge(upgraded_stamp)
        } else {
            return null;
        }
    }
}