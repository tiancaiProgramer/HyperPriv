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
            if(m.getName().equals("getInt"))return (Integer)values.getOrDefault(a[0],a[1]);
            if(m.getName().equals("edit")){
                Set<String> removals=new HashSet<>();
                Map<String,Integer> updates=new HashMap<>();
                return Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{SharedPreferences.Editor.class},(e,em,ea)->{
                    if(em.getName().equals("remove")){removals.add((String)ea[0]);return e;}
                    if(em.getName().equals("putInt")){updates.put((String)ea[0],(Integer)ea[1]);return e;}
                    if(em.getName().equals("commit")){
                        commits++;if(commitSuccess){for(String key:removals)values.remove(key);values.putAll(updates);}
                        return commitSuccess;
                    }
                    throw new UnsupportedOperationException(em.getName());
                });
            }
            throw new UnsupportedOperationException(m.getName());
        });}
    }
    public static void main(String[] args){
        Store local=new Store();
        local.values.put("master",false);local.values.put("core_previous_signature",true);
        local.values.put(Compatibility.KEY,false);
        local.values.put("clipboard_packages","fixture.allowed");local.values.put("clipboard_close_text","");
        local.values.put("key_actions",false);local.values.put("key_bindings","saved-key-actions");
        local.values.put("clipboard_opacity",42);local.values.put("initialized",true);
        local.values.put(Appearance.STATUS,true);local.values.put(Appearance.Value.NOTIFICATIONS.key,9);
        local.values.put(Appearance.Value.PILL_OPACITY.key,45);
        Map<String,Object> expected=new HashMap<>(local.values);local.values.put("obsolete_fixture_flag",true);
        local.values.put("appearance_status_y",-3);
        require(Settings.cleanStoredKeys(local.prefs(),true),"local cleanup failed");
        require(local.values.equals(expected),"supported configuration changed during cleanup");
        require(Settings.cleanStoredKeys(local.prefs(),true)&&local.commits==1,"unchanged migration committed again");
        Store oldClock=new Store();
        oldClock.values.put(Appearance.Value.CLOCK_POSITION.key,3);
        require(Settings.appearance(oldClock.prefs(),Appearance.Value.CLOCK_POSITION)==0
                &&Settings.cleanStoredKeys(oldClock.prefs(),true)&&oldClock.values.isEmpty(),
                "former centered clock preference must be cleared and follow the system");
        Store remote=new Store();remote.values.putAll(expected);remote.values.put("obsolete_fixture_text","old");
        remote.values.put("appearance_status_y",3);
        remote.values.put(Appearance.REFRESH_REQUEST,23);
        require(Settings.cleanStoredKeys(remote.prefs(),false),"remote cleanup failed");
        expected.remove("initialized");
        expected.put(Appearance.REFRESH_REQUEST,23);
        require(remote.values.equals(expected)&&!Settings.storedKey(Appearance.REFRESH_REQUEST,true),
                "remote appearance refresh request must survive synchronization");
        Store failure=new Store();failure.values.put("master",false);failure.values.put("obsolete_fixture_flag",true);failure.commitSuccess=false;
        require(!Settings.cleanStoredKeys(failure.prefs(),true),"failed commit reported success");
        require(failure.values.size()==2&&Boolean.FALSE.equals(failure.values.get("master")),"failed cleanup changed existing preferences");
        Store duo=new Store();
        duo.values.put("appearance_duo_network",1);
        duo.values.put(Appearance.Value.SIGNAL.key,4);
        duo.values.put(Appearance.Value.NETWORK_ORDER.key,2);
        require(Settings.appearance(duo.prefs(),Appearance.Value.SIGNAL)==Appearance.DUO_SIGNAL_STYLE,
                "legacy remote Duo must load before migration");
        require(Settings.cleanStoredKeys(duo.prefs(),true)&&!duo.values.containsKey("appearance_duo_network")
                &&duo.values.get(Appearance.Value.SIGNAL.key).equals(Appearance.DUO_SIGNAL_STYLE)
                &&duo.values.get(Appearance.Value.NETWORK_ORDER.key).equals(2),
                "legacy Duo migration must preserve the saved order");
        Store battery=new Store();
        battery.values.put(Appearance.Value.BATTERY.key,Appearance.BATTERY_RING_STYLE);
        battery.values.put("appearance_top_battery_bar",1);
        battery.values.put("appearance_battery_mark",1);
        battery.values.put(Appearance.Value.CUTOUT_RING_GAP.key,4);
        require(Settings.appearance(battery.prefs(),Appearance.Value.BATTERY)==Appearance.BATTERY_TOP_STYLE,
                "legacy top bar must migrate into the style selector");
        battery.values.put("appearance_cutout_ring",1);
        require(Settings.cleanStoredKeys(battery.prefs(),true)
                &&Settings.appearance(battery.prefs(),Appearance.Value.BATTERY)==Appearance.BATTERY_CUTOUT_STYLE
                &&Settings.appearance(battery.prefs(),Appearance.Value.CUTOUT_RING_GAP)==4
                &&!battery.values.containsKey("appearance_top_battery_bar")
                &&!battery.values.containsKey("appearance_cutout_ring")
                &&!battery.values.containsKey("appearance_battery_mark"),
                "legacy cutout ring must take priority over the top bar and preserve its size");
        battery.values.put(Appearance.Value.BATTERY.key,Appearance.BATTERY_TOP_STYLE);
        battery.values.put("appearance_cutout_ring",1);
        require(Settings.appearance(battery.prefs(),Appearance.Value.BATTERY)==Appearance.BATTERY_TOP_STYLE,
                "the unified style must take priority over a stale flag");
        System.out.println("Preference migration: obsolete keys removed, supported values and initialization preserved, no-op and failed commit handled");
    }
}
