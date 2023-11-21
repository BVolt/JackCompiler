package com.mycompany.jackcompiler;

public class Identifier {
    private int index;
    private String type;
    private String kind;
    
    public Identifier(int index, String type, String kind){
        this.index = index;
        this.type = type;
        this.kind = kind;
    }
    
    public int getIndex(){return this.index;}
    public String getType(){return this.type;}
    public String getKind(){return this.kind;}
}
