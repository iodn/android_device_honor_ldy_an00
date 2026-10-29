// SPDX-License-Identifier: Apache-2.0
package org.lineageos.ldy_an00.display;
import java.util.Set;
import java.util.HashSet;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.json.JSONObject;

/** Fixed selected firmware's default non-foldable phone APS policy. */
record StockFullscreenProfile(Set<String> packages,long debounceMillis,int initialWidth,int initialHeight) {
    StockFullscreenProfile { packages=Set.copyOf(packages); }
    static StockFullscreenProfile load(byte[] bytes) throws Exception {
        if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
                .equals("b4566bb4f80f2eeb85a3077d900a142586d6722382c318515e8006c878358bae"))throw new IllegalArgumentException("APS profile digest");
        var json=new JSONObject(new String(bytes,java.nio.charset.StandardCharsets.UTF_8));
        if(json.getInt("schema")!=1 || !json.getBoolean("defaultNonFoldPhone")
                || json.getLong("debounceMillis")!=700)
            throw new IllegalArgumentException("APS profile mode");
        Set<String> packages=new HashSet<>();
        var rows=json.getJSONArray("packages");
        for(int i=0;i<rows.length();i++)
            if(!packages.add(rows.getString(i)))throw new IllegalArgumentException("APS duplicates");
        if(packages.size()!=16)throw new IllegalArgumentException("APS profile count");
        int width=json.getInt("initialWidth"),height=json.getInt("initialHeight");
        if(width<=0 || height<=0)throw new IllegalArgumentException("APS initial dimensions");
        return new StockFullscreenProfile(packages,700,width,height);
    }
}
