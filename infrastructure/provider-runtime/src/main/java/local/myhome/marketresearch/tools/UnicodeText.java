package local.myhome.marketresearch.tools;

/** Text bounds expressed in UTF-16 units, preserving complete Unicode surrogate pairs. */
public final class UnicodeText {
    private UnicodeText() {}

    public static String prefix(String text,int limit) {
        if(limit<0)throw new IllegalArgumentException("Negative text limit");
        if(text==null||text.length()<=limit)return text;
        int end=limit;
        if(end>0&&Character.isHighSurrogate(text.charAt(end-1))&&Character.isLowSurrogate(text.charAt(end)))end--;
        return text.substring(0,end);
    }
}
