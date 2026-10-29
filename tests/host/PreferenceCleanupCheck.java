package io.github.hyperpriv;

import android.content.SharedPreferences;
import java.lang.reflect.*;
import java.util.*;

/** Exercises actual migration edits without resetting any supported value. */
public final class PreferenceCleanupCheck {
    private static void require(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static final class Store {
        final Map<String,Object> values=new HashMap<>();
        boolean commitSuccess=true;int commits;
        SharedPreferences prefs(){return (SharedPreferences)Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.class},(p,m,a)->{
            if(m.getName().equals("getAll"))return new HashMap<>(values);
            if(m.getName().equals("edit")){
                Set<String> removals=new HashSet<>();
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.Editor.class},(e,em,ea)->{
                    if(em.getName().equals("remove")){removals.add((String)ea[0]);return e;}
                    if(em.getName().equals("commit")){commits++;if(commitSuccess)for(String key:removals)values.remove(key);return commitSuccess;}
                    throw new UnsupportedOperationException(em.getName());
                });
            }
            throw new UnsupportedOperationException(m.getName());
        });}
    }
    public static void main(String[] args){
        Store local=new Store();
        local.values.put("master",false);local.values.put("core_previous_signature",true);
        local.values.put("clipboard_packages","fixture.allowed");local.values.put("clipboard_close_text","");
        local.values.put("clipboard_opacity",42);local.values.put("initialized",true);
        Map<String,Object> expected=new HashMap<>(local.values);local.values.put("obsolete_fixture_flag",true);
        require(Settings.cleanStoredKeys(local.prefs(),true),"local cleanup failed");
        require(local.values.equals(expected),"supported configuration changed during cleanup");
        require(Settings.cleanStoredKeys(local.prefs(),true)&&local.commits==1,"unchanged migration committed again");
        Store remote=new Store();remote.values.putAll(expected);remote.values.put("obsolete_fixture_text","old");
        require(Settings.cleanStoredKeys(remote.prefs(),false),"remote cleanup failed");
        expected.remove("initialized");require(remote.values.equals(expected),"remote keys did not follow schema");
        Store failure=new Store();failure.values.put("master",false);failure.values.put("obsolete_fixture_flag",true);failure.commitSuccess=false;
        require(!Settings.cleanStoredKeys(failure.prefs(),true),"failed commit reported success");
        require(failure.values.size()==2&&Boolean.FALSE.equals(failure.values.get("master")),"failed cleanup changed existing preferences");
        System.out.println("Preference migration: obsolete keys removed, supported values and initialization preserved, no-op and failed commit handled");
    }
}
