package io.github.hyperpriv;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
/** Root-only read-only query of module identity/scope metadata; never reads module preferences. */
public final class ModuleScopeAudit {
 public static void main(String[] args){
  SQLiteDatabase db=SQLiteDatabase.openDatabase("/data/adb/lspd/config/modules_config.db",null,SQLiteDatabase.OPEN_READONLY|SQLiteDatabase.NO_LOCALIZED_COLLATORS);
  try{
   for(String table:new String[]{"modules","scope"})try(Cursor c=db.rawQuery("PRAGMA table_info("+table+")",null)){
    while(c.moveToNext())System.out.println("COLUMN\t"+table+"\t"+c.getString(1));
   }
   try(Cursor c=db.rawQuery("SELECT module_pkg_name FROM modules ORDER BY module_pkg_name",null)){
    while(c.moveToNext())System.out.println("REGISTERED_MODULE\t"+c.getString(0));
   }
   String sql="SELECT m.module_pkg_name,s.app_pkg_name,s.user_id FROM modules m JOIN scope s ON m.module_pkg_name=s.module_pkg_name WHERE s.app_pkg_name IN ('system','android','com.miui.packageinstaller','com.miui.securitycenter','com.miui.guardprovider','com.android.settings','com.miui.powerkeeper','com.xiaomi.joyose','com.lbe.security.miui','com.mobiletools.systemhelper') ORDER BY m.module_pkg_name,s.app_pkg_name";
   try(Cursor c=db.rawQuery(sql,null)){while(c.moveToNext())System.out.println("OVERLAP\t"+c.getString(0)+"\t"+c.getString(1)+"\t"+c.getInt(2));}
  }finally{db.close();}
 }
}
