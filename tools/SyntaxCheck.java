import javax.tools.*;
import com.sun.source.util.JavacTask;
import java.nio.file.*;
import java.util.*;

/** Parsing only: does not resolve Android/Kotlin dependencies and is NOT an APK compile. */
class SyntaxCheck {
    public static void main(String[] args)throws Exception {
        JavaCompiler c=ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> errors=new DiagnosticCollector<>();
        try(StandardJavaFileManager fm=c.getStandardFileManager(errors,null,null)) {
            List<java.io.File> files=new ArrayList<>();
            try(var paths=Files.walk(Path.of(args[0]))){paths.filter(p->p.toString().endsWith(".java")).forEach(p->files.add(p.toFile()));}
            JavacTask task=(JavacTask)c.getTask(null,fm,errors,List.of("-proc:none"),null,fm.getJavaFileObjectsFromFiles(files));
            task.parse();boolean failed=false;
            for(var d:errors.getDiagnostics())if(d.getKind()==Diagnostic.Kind.ERROR){System.out.println(d);failed=true;}
            if(failed)System.exit(1);
            System.out.println(files.size()+" Java files parsed without syntax errors. Android symbols and Kotlin integration NOT type-checked.");
        }
    }
}
