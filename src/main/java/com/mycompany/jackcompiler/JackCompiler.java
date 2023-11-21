package com.mycompany.jackcompiler;
import java.io.*;

public class JackCompiler {

    public static void main(String[] args) {
        String path = (args.length == 0) ? "tests/Pong": args[0];
        if(path.split("\\.").length<=1){
            File dir = new File(path);
            File[] directory = dir.listFiles();
            for(File file: directory){
                if(file.getName().split("\\.")[1].equals("jack")){
                    translateFile(path+"/"+file.getName());
                }
            }
        }
        else
            translateFile(path);
        
    }
    
    public static void translateFile(String path){
        String name = path.split("\\.")[0];
        CompilationEngine engine = new CompilationEngine(openInFile(path),openOutFile(name + ".vm"));
        engine.CompileClass();
        engine.closeFiles();
    }
    
    public static BufferedReader openInFile(String path){
        try{
            return new BufferedReader(new FileReader(path));
        }catch(IOException e){}
        return null;
    }
    
    public static BufferedWriter openOutFile(String path){
        try{
            return new BufferedWriter(new FileWriter(path));
        }catch(IOException e){}
        return null;
    }
}
