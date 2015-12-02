#!/bin/sh

#****************************************************************************************
# Run this script to move your CM12 device move back to a CM11 state. This is
# useful when you want to manually test CM11 to CM12 upgrade without reflashing the device
#***************************************************************************************

#Delete all themes related data
adb shell sqlite3 /data/data/com.android.providers.settings/databases/settings.db "DELETE FROM secure WHERE name='themeConfig'";
adb shell sqlite3 /data/data/com.android.providers.settings/databases/settings.db "DELETE FROM secure WHERE name='theme_prev_boot_api_level'";
adb shell sqlite3 /data/data/com.android.providers.settings/databases/settings.db "pragma user_version=115"
adb shell sqlite3 /data/data/com.android.providers.settings/databases/settings.db "DELETE FROM secure WHERE name='default_theme_package'"
adb shell sqlite3 /data/data/com.android.providers.settings/databases/settings.db "DELETE FROM secure WHERE name='default_theme_components'"

#HEXO Config (Comment HOLO if you use this)
adb shell sqlite3 /data/data/com.android.providers.settings/databases/settings.db "INSERT INTO secure (name,value) VALUES('themeConfig','{\"default\":{\"mOverlayPkgName\":\"com.cyngn.hexo\",\"mIconPkgName\":\"com.tung91.mianogen\",\"mFontPkgName\":\"bigwave.thyrus.darkuinte\"}}')";

#HOLO Config (Comment HEXO if you use this)
#adb shell sqlite3 /data/data/com.android.providers.settings/databases/settings.db "INSERT INTO secure (name,value) VALUES('themeConfig','{\"default\":{\"mOverlayPkgName\":\"holo\",\"mIconPkgName\":\"com.tung91.mianogen\",\"mFontPkgName\":\"bigwave.thyrus.darkuinte\"}}')";


#Default Theme Package
adb shell sqlite3 /data/data/com.android.providers.settings/databases/settings.db "INSERT INTO secure (name,value) VALUES('default_theme_package', 'com.cyngn.hexo')"
adb shell 'sqlite3 /data/data/com.android.providers.settings/databases/settings.db "INSERT INTO secure (name,value) VALUES(\"default_theme_components\", \"mods_overlays\")"'

#Print out the db
adb shell sqlite3 /data/data/com.android.providers.settings/databases/settings.db "SELECT * from secure"


#ThemesProvider's default theme is called "Holo"
adb shell sqlite3 /data/data/org.cyanogenmod.themes.provider/databases/themes.db "UPDATE themes SET pkg_name='holo', title='Holo' WHERE pkg_name='system'"
adb shell sqlite3 /data/data/org.cyanogenmod.themes.provider/databases/themes.db "pragma user_version=10"

adb shell sqlite3 /data/data/org.cyanogenmod.themes.provider/databases/themes.db "SELECT * FROM themes"
