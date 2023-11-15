package com.mycompany.jackcompiler;
import java.io.*;

public class VMWriter {
    private BufferedWriter outFile;
    
    public VMWriter(BufferedWriter file){
        outFile = file;
    }
    
    public void writePush(String segment, int index){
        writeLine(String.format("push %s %d", segment, index));
    }
    
    public void writePop(String segment, int index){
        writeLine(String.format("pop %s %d", segment, index));
    }
    
    public void writeArithmetic(String command){
        switch(command){
            case "mult"-> writeLine("call Math.multiply 2");
            case "div" -> writeLine("call Math.divide 2");
            default->writeLine(command);
        }
    }
        
    public void WriteLabel(String label){
        writeLine("label "+label);
    }
    
    public void WriteGoto(String label){
        writeLine("goto " + label);
    }
    
    public void WriteIf(String label){
        writeLine("if-goto " + label);
    }
    
    public void WriteCall(String name, int nArgs){
        writeLine(String.format("call %s %d", name, nArgs));
    }
    
    public void WriteFunction(String name, int nLocals){
        writeLine(String.format("function %s %d", name, nLocals));
    }
    
    public void writeReturn(){
        writeLine("return");
    }
    
    public void writeLine(String s){
        try{
            outFile.write(s+"\n");
        }catch(IOException e){
        
        }
    }
    
    public void close()throws IOException{
        outFile.close();    
    }
}
