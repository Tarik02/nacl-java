package com.caligochat.nacl;

/**
 * Created by wfreeman on 2/11/15.
 */
public class SecretBox {
	public static int OVERHEAD = Poly1305.TAG_SIZE;

	// subKey = byte[32], counter = byte[16], nonce = byte[24], key = byte[32]
	private static void setup(byte subKey[], byte counter[], byte nonce[], byte key[]) {
		// We use XSalsa20 for encryption so first we need to generate a
		// key and nonce with HSalsa20.
		byte hNonce[] = new byte[16];
		for(int i = 0; i < hNonce.length; i++) {
			hNonce[i] = nonce[i];
		}
		byte newSubKey[] = Salsa.HSalsa20(hNonce, key, Salsa.SIGMA);
		for(int i = 0; i < subKey.length; i++) {
			subKey[i] = newSubKey[i];
		}

		for(int i = 0; i < nonce.length-16; i++) {
			counter[i] = nonce[i+16];
		}
	}

	public static byte[] seal(byte message[], byte nonce[], byte key[]) {
		byte subKey[] = new byte[32];
		byte counter[] = new byte[16];
		setup(subKey, counter, nonce, key);

		// The Poly1305 key is generated by encrypting 32 bytes of zeros. Since
		// Salsa20 works with 64-byte blocks, we also generate 32 bytes of
		// keystream as a side effect.
		byte firstBlock[] = new byte[64];
		firstBlock = Salsa.XORKeyStream(firstBlock, counter, subKey);

		byte poly1305Key[] = new byte[32];
		for(int i = 0; i < poly1305Key.length; i++) {
			poly1305Key[i] = firstBlock[i];
		}

		byte ret[] = new byte[message.length+Poly1305.TAG_SIZE];
		for(int i=0; i < ret.length; i++) {
			ret[i] = 0;
		}

		// We XOR up to 32 bytes of message with the keystream generated from
		// the first block.
		byte firstMessageBlock[] = new byte[message.length];
		if (message.length > 32) {
			firstMessageBlock = new byte[32];
			for(int i = 0; i < 32; i++) {
				firstMessageBlock[i] = message[i];
			}
		}

		for (int i = 0; i < firstMessageBlock.length; i++) {
			ret[i+Poly1305.TAG_SIZE] = (byte)(firstBlock[32+i] ^ firstMessageBlock[i]);
		}
		// chop off the first block
		byte restmessage[] = new byte[message.length - firstMessageBlock.length];
		for(int i = 0; i < restmessage.length; i++) {
			restmessage[i] = message[i+firstMessageBlock.length];
		}

		// Now encrypt the rest.
		counter[8] = 1;
		byte out[] = Salsa.XORKeyStream(restmessage, counter, subKey);
		for(int i = 0; i < ret.length-(firstMessageBlock.length+Poly1305.TAG_SIZE); i++) {
			ret[i+firstMessageBlock.length+Poly1305.TAG_SIZE] = out[i];
		}

		byte ciphertext[] = new byte[ret.length-Poly1305.TAG_SIZE];
		for(int i = 0; i < ciphertext.length; i++) {
			ciphertext[i] = ret[i+Poly1305.TAG_SIZE];
		}
		byte tag[] = Poly1305.sum(ciphertext, poly1305Key);
		for(int i = 0; i < tag.length; i++) {
			ret[i] = tag[i];
		}
		return ret;
	}

	public static byte[] open(byte box[], byte nonce[], byte key[]) throws NaclException {
		byte subKey[] = new byte[32];
		byte counter[] = new byte[16];
		setup(subKey, counter, nonce, key);

		// The Poly1305 key is generated by encrypting 32 bytes of zeros. Since
		// Salsa20 works with 64-byte blocks, we also generate 32 bytes of
		// keystream as a side effect.
		byte firstBlock[] = new byte[64];
		for(int i = 0; i < firstBlock.length; i++) {
			firstBlock[i] = 0;
		}
		firstBlock = Salsa.XORKeyStream(firstBlock, counter, subKey);

		byte poly1305Key[] = new byte[32];
		for (int i = 0; i < poly1305Key.length; i++) {
			poly1305Key[i] = firstBlock[i];
		}
		byte tag[] = new byte[Poly1305.TAG_SIZE];
		for (int i = 0; i < tag.length; i++) {
			tag[i] = box[i];
		}

		byte cipher[] = new byte[box.length - Poly1305.TAG_SIZE];
		for(int i = 0; i < cipher.length; i++) {
			cipher[i] = box[i + Poly1305.TAG_SIZE];
		}
		if (!Poly1305.verify(tag, cipher, poly1305Key)) {
			throw new NaclException("not valid");
		}

		byte ret[] = new byte[box.length-OVERHEAD];
		for(int i=0; i < ret.length;i++) {
			ret[i] = box[i+OVERHEAD];
		}
		// We XOR up to 32 bytes of box with the keystream generated from
		// the first block.
		byte firstMessageBlock[] = new byte[ret.length];
		if (ret.length > 32) {
			firstMessageBlock = new byte[32];
		}
		for (int i = 0; i < firstMessageBlock.length; i++) {
			firstMessageBlock[i] = ret[i];
		}
		for  (int i = 0; i < firstMessageBlock.length; i++) {
			ret[i] = (byte)(firstBlock[32+i] ^ firstMessageBlock[i]);
		}

		counter[8] = 1;
		byte newbox[] = new byte[box.length - (firstMessageBlock.length + OVERHEAD)];
		for(int i=0; i < newbox.length; i++) {
			newbox[i] = box[i+firstMessageBlock.length+OVERHEAD];
		}
		byte rest[] = Salsa.XORKeyStream(newbox, counter, subKey);
		// Now decrypt the rest.

		for(int i = firstMessageBlock.length; i < ret.length; i++) {
			ret[i] = rest[i-firstMessageBlock.length];
		}
		return ret;
	}
}