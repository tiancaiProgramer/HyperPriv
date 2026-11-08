package com.miui.packageInstaller.model;
public abstract class CloudResult {
 public static final class Success extends CloudResult {
  private final CloudParams data;public Success(CloudParams data){this.data=data;}public CloudParams getData(){return data;}
 }
}
