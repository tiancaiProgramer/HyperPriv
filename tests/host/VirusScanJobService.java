package com.miui.antivirus.service;
/** Host-only caller marker. Never runs a system job. */
public final class VirusScanJobService {
 public interface Call {Object run()throws Throwable;}
 public static Object invoke(Call callback)throws Throwable{return callback.run();}
}
