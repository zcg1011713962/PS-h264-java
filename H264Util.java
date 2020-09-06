/**
 * 
 */
package com.csg.iot.video.recording.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;

import javax.websocket.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * h264转码
 * 
 * @author zhengchunguang
 * @date 2020年7月6日
 */
public class H264Util {

	private static Logger logger = LoggerFactory.getLogger(H264Util.class);
	private ByteArrayOutputStream psStream = new ByteArrayOutputStream();
	private ByteArrayOutputStream h264Stream = new ByteArrayOutputStream();
	private byte[] resByte = null;
	
	
	/**
	 * 处理数据
	 * @param channel
	 * @param psByte
	 */
	public void h264TaskHandler(Session channel, byte[] psByte) {
		if (psByte == null) {
			return;
		}
		convert(psByte, channel, null);
	}
	
	
	/**
	 * 下载数据
	 * @param channel
	 * @param psByte
	 * @param fileName
	 */
	public void h264TaskHandler(byte[] psByte,RandomAccessFile randomAccessFile) {
		if (psByte == null) {
			return;
		}
		convert(psByte, null, randomAccessFile);
	}
	
	
	/**
	 * 海康数据处理
	 * @param psByte
	 * @param channel
	 */
	private void convert(byte[] psByte, Session channel, RandomAccessFile randomAccessFile) {
		
		try {
			
			// 上次剩余数据处理
			writeResidue();
			// 本次数据
			psStream.write(psByte, 12, psByte.length-12);
			// 总数据
			byte[] dataByte = psStream.toByteArray();
			int length = dataByte.length;
			// 重置
			psStream.reset();
			
			for (int i = 0; i < length;) {
				// 不足5位 比较索引
				if (length - 1 < i + 4) {
					i = residue(length, i, dataByte);
					return;
				}

			    if (dataByte[i] == (byte) 0x00 && dataByte[i + 1] == (byte) 0x00 && dataByte[i + 2] == (byte) 0x01
						&& dataByte[i + 3] == (byte) 0xba) {
					// 不足14位
					if (length - 1 < i + 13) {
						i = residue(length, i, dataByte);
						return;
					}
					// PS HEADER 取包头第14字节
					// 14字节后填充数据的长度
					int sk = dataByte[i + 13] & 0x07;
					if (length - 1 < i + 13 + sk) {
						i = residue(length, i, dataByte);
						return;
					}
					i = i + 13 + sk + 1;
				} else if (dataByte[i] == (byte) 0x00 && dataByte[i + 1] == (byte) 0x00 && dataByte[i + 2] == (byte) 0x01
						&& dataByte[i + 3] == (byte) 0xbb) {
					// 不足6位
					if (length - 1 < i + 5) {
						i = residue(length, i, dataByte);
						return;
					}
					// PS system header -系统标题头
					// 00 00 01 BB 后两位代表长度
					String bb = String.format("%02x ", dataByte[i + 4]) + String.format("%02x ", dataByte[i + 5]);
					int sk = Integer.parseInt(bb.replaceAll(" ", ""), 16);
					if (length - 1 < i + 3 + 2 + sk) {
						i = residue(length, i, dataByte);
						return;
					}
					i = i + 3 + 2 + sk + 1;
				} else if (dataByte[i] == (byte) 0x00 && dataByte[i + 1] == (byte) 0x00 && dataByte[i + 2] == (byte) 0x01
						&& dataByte[i + 3] == (byte) 0xbc) {
					// 不足6位
					if (length - 1 < i + 5) {
						i = residue(length, i, dataByte);
						return;
					}
					// PS system Map
					// 00 00 01 BC 后两位代表长度
					String bb = String.format("%02x ", dataByte[i + 4]) + String.format("%02x ", dataByte[i + 5]);
					int sk = Integer.parseInt(bb.replaceAll(" ", ""), 16);
					if (length - 1 < i + 3 + 2 + sk) {
						i = residue(length, i, dataByte);
						return;
					}
					i = i + 3 + 2 + sk + 1;
				} else if (dataByte[i] == (byte) 0x00 && dataByte[i + 1] == (byte) 0x00 && dataByte[i + 2] == (byte) 0x01
						&& dataByte[i + 3] == (byte) 0xe0) {
					// 不足6位
					if (length - 1 < i + 5) {
						i = residue(length, i, dataByte);
						return;
					}
					// 获取pes包长
					String pes = String.format("%02x ", dataByte[i + 4]) + String.format("%02x ", dataByte[i + 5]);
					int pesLen = Integer.parseInt(pes.replaceAll(" ", ""), 16);
					if (length - 1 < i + 8) {
						i = residue(length, i, dataByte);
						return;
					}
					// 获取pes加扰控制位
					int streamFlag = dataByte[i + 6] & 0xC0;
					if(streamFlag == 0x80) {
						//解析pts
						int ptsFlag = dataByte[i + 7] & 0xC0;
						if((ptsFlag == 0x00) ||(ptsFlag == 0x80) || (ptsFlag == 0xc0)) {
							// pts数据长度
							String pts = String.format("%02x ", dataByte[i + 8]);
							int sk = Integer.parseInt(pts.replaceAll(" ", ""), 16);
							if (length -1  < i + 8 + sk) {
								i = residue(length, i, dataByte);
								return;
							}
							// 跳过00 00 00 e0 及无用数据
							i =  i + 8 + sk+ 1;
						}
					}else {
						// pts数据长度
						String pts = String.format("%02x ", dataByte[i + 8]);
						int sk = Integer.parseInt(pts.replaceAll(" ", ""), 16);
						if (length -1  < i + 8 + sk + pesLen) {
							i = residue(length, i, dataByte);
							return;
						}
						// 不解析此段流
						i= i+pesLen+1;
						logger.info("----------------------不解析此段流");
					}
				} else if (dataByte[i] == (byte) 0x00 && dataByte[i + 1] == (byte) 0x00 && dataByte[i + 2] == (byte) 0x01
						&& dataByte[i + 3] == (byte) 0xc0) {
					// 不足6位
					if (length - 1 < i + 5) {
						i = residue(length, i, dataByte);
						return;
					}
					// 00 00 01 C0 音频头 后两位代表长度
					String c0 = String.format("%02x ", dataByte[i + 4]) + String.format("%02x ", dataByte[i + 5]);
					int sk = Integer.parseInt(c0.replaceAll(" ", ""), 16);
					if (length - 1 < i + 5 + sk) {
						i = residue(length, i, dataByte);
						return;
					}
					i = i + 5 + sk + 1;
				}else {
					if(randomAccessFile == null) {
						// 负载数据
						i = write(dataByte, i, channel);
					} else {
						// 写入文件
						i = writeToFile(dataByte, i, randomAccessFile);
					}
				}
			}
			dataByte = null;
		} catch (Exception e) {
			logger.info("{h264TaskHandler}", e);
		}
	}
	
	
	/**
	 * 写入文件
	 * @param dataByte
	 * @param i
	 * @param channel
	 */
	private int writeToFile(byte [] dataByte, int i, RandomAccessFile randomAccessFile) {
		try {
			randomAccessFile.write(dataByte[i++]);
			
		} catch (IOException e) {
			e.printStackTrace();
		}
		return i;
	}
	
	
	/**
	 * 上次剩余
	 */
	private void writeResidue(){
		if (resByte != null && resByte.length > 0) {
			// 上次剩余
			psStream.write(resByte, 0, resByte.length);
			resByte = null;
		}
	}
	
	/**
	 * 剩余数据处理
	 * @param length
	 * @param i
	 * @param dataByte
	 * @return
	 */
	private int residue(int length, int i, byte [] dataByte) {
		// 剩余位数
		int resLength = length - i;
		// 存入剩余数组
		resByte = new byte[resLength];
		for (int j = 0; j < resLength; j++) {
			resByte[j] = dataByte[i++];
		}
		return i;
	}
	
	
	/**
	 * 写负载数据
	 * @param dataByte
	 * @param i
	 * @param channel
	 * @throws IOException 
	 */
	private int write(byte [] dataByte, int i, Session channel) throws IOException {
		//ByteBuf byteBuf = null;
		
		if(	dataByte[i] == (byte) 0x00 && dataByte[i + 1] == (byte) 0x00 && dataByte[i + 2] == (byte) 0x00 && dataByte[i + 3] == (byte) 0x01 && dataByte[i + 4] == (byte) 0x67
			||dataByte[i] == (byte) 0x00 && dataByte[i + 1] == (byte) 0x00 && dataByte[i + 2] == (byte) 0x00 && dataByte[i + 3] == (byte) 0x01 && dataByte[i + 4] == (byte) 0x68
			||dataByte[i] == (byte) 0x00 && dataByte[i + 1] == (byte) 0x00 && dataByte[i + 2] == (byte) 0x00 && dataByte[i + 3] == (byte) 0x01 && dataByte[i + 4] == (byte) 0x65
			||dataByte[i] == (byte) 0x00 && dataByte[i + 1] == (byte) 0x00 && dataByte[i + 2] == (byte) 0x00 && dataByte[i + 3] == (byte) 0x01 && dataByte[i + 4] == (byte) 0x61
		) {
			byte[] h264Data = h264Stream.toByteArray();
			if(h264Data.length > 0) {
				//byteBuf = Unpooled.wrappedBuffer(h264Data);
				ByteBuffer byteBuffer=ByteBuffer.wrap(h264Data);
				// 推送数据
				//channel.writeAndFlush(new BinaryWebSocketFrame(byteBuf));
				if(channel.isOpen()) {
					channel.getBasicRemote().sendBinary(byteBuffer);
				}
				h264Stream.reset();
			}
		}
		h264Stream.write(dataByte[i++]);
		return i;
	}
	
	/**
	 * 关闭流
	 */
	public void close() {
		try {
			if(h264Stream != null) {
				h264Stream.close();
			}
			if(psStream != null) {
				psStream.close();
			}
		} catch (IOException e) {
			logger.error(e.getMessage());
		}
	}
	
}
