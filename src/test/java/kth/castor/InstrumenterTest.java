package kth.castor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class InstrumenterTest
{

   Instrumenter inst;

   @Before
   public void instantiateInstrumenter()
   {
      inst = new Instrumenter();
   }

   @Test
   public void checkThatIsNotNull() throws IOException
   {
      inst.printDependencies("src/main/resources/weka-stable-3.8.3-SNAPSHOT-jar-with-dependencies.jar", "org/apache/commons");
      assertNotNull(inst.getMethodsCalled());
   }

   @Test
   public void check() throws IOException
   {
      inst.printDependencies("src/test/resources/uselib/target/uselib-1.0-SNAPSHOT.jar", "se/kth/castor/types");
      assertNotNull(inst.getMethodsCalled());
   }

   public DependencyTree test(InputStream classFileInputStream, String dependencyPkg) throws IOException
   {
      DependencyTree dt = new DependencyTree();
      try {
         ClassReader cr = new ClassReader(classFileInputStream);
         ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
         ClassVisitor cv = new ClassAdapter(cw, dependencyPkg, dt);
         cr.accept(cv, 0);
      } catch (IOException e) {
         e.printStackTrace();
      } finally {
         classFileInputStream.close();
      }
      return dt;
   }

   public static JSONArray readArrayFromFile(File f)
   {
      JSONArray jsonArray = null;
      BufferedReader br = null;
      try {
         br = new BufferedReader(new FileReader(f));
         StringBuilder sb = new StringBuilder();
         String line = br.readLine();
         while (line != null) {
            sb.append(line);
            line = br.readLine();
         }

         jsonArray = new JSONArray(sb.toString());
      } catch (Exception e) {
         e.printStackTrace();
      } finally {
          if (br != null) {
              try {
                  br.close();
              } catch (IOException e) {
                  e.printStackTrace();
              }
          }
      }

      return jsonArray;
   }

   public Map<String, Map<String, Set<String>>> getExpected(File expectedFile)
   {
      JSONArray raw = readArrayFromFile(expectedFile);
      Map<String, Map<String, Set<String>>> expected = new HashMap<>();
      try {
         for (int i = 0; i < raw.length(); i++) {
            JSONObject cl = raw.getJSONObject(i);
            Map<String, Set<String>> usedTypes = new HashMap<>();
            String className = cl.getString("className");
            JSONArray ar = cl.getJSONArray("uses");
            for (int j = 0; j < ar.length(); j++) {
               JSONObject usedTypeRaw = ar.getJSONObject(j);
               String usedType = usedTypeRaw.getString("usedType");
               JSONArray els = usedTypeRaw.getJSONArray("usedElements");
               Set<String> usedElements = new HashSet<>();
               for (int k = 0; k < els.length(); k++) {
                  usedElements.add(els.getString(k));
               }
               usedTypes.put(usedType, usedElements);
            }
            expected.put(className, usedTypes);
         }
      } catch (JSONException e) {
         e.printStackTrace();
      }
      return expected;
   }

   @Test
   public void checkClassPerClass() throws IOException
   {
      String pathToJar = "src/test/resources/uselib/target/uselib-1.0-SNAPSHOT.jar";
      String dependencyPkg = "se/kth/castor/types";

      Set<String> exceptions = new HashSet<>();
      exceptions.add("se/kth/castor/user/ImplAbsParam0");//Method implemented in Abstract Class is call on Concrete class instance
      exceptions.add("se/kth/castor/user/ImplAbsParam4");
      exceptions.add("se/kth/castor/user/ImplAbsParam6");
      exceptions.add("se/kth/castor/user/ObjectSimpleDecl5");
      exceptions.add("se/kth/castor/user/AbsSimpleDecl5");
      exceptions.add("se/kth/castor/user/InterSimpleDecl5");
      exceptions.add("se/kth/castor/user/ImplAbsParam2");
      exceptions.add("se/kth/castor/user/AbsReturn5");
      exceptions.add("se/kth/castor/user/InterReturn5");
      exceptions.add("se/kth/castor/user/ObjectReturn5");
      exceptions.add("se/kth/castor/user/ImplAbsSimpleCall0");
      exceptions.add("se/kth/castor/user/ImplAbsSimpleCall2");
      exceptions.add("se/kth/castor/user/ImplAbsSimpleCall4");
      exceptions.add("se/kth/castor/user/ImplAbsSimpleCall6");
      exceptions.add("se/kth/castor/created/UseAnno"); //Access to static field .class does not exist in bytecode

      Map<String, Map<String, Set<String>>> expected = getExpected(new File("src/test/resources/expected.json"));

      JarFile jarFile = new JarFile(new File(pathToJar));
      Enumeration<JarEntry> entries = jarFile.entries();
      //DependencyTree dt = new DependencyTree();
      while (entries.hasMoreElements()) {
         JarEntry entry = entries.nextElement();
         String entryName = entry.getName();
         if (entryName.endsWith(".class")) {
             if (exceptions.contains(entryName.replace(".class", ""))) {
                 continue;
             }
            InputStream classFileInputStream = jarFile.getInputStream(entry);
            DependencyTree dt = test(classFileInputStream, dependencyPkg);
            Map<String, Set<String>> exp = expected.get(entryName.replace(".class", "").replace("/", "."));
            if (exp == null) {
               assertTrue(dt.dt.size() == 0);
            } else {
               assertTrue(dt.dt.size() == exp.size());
               for (String expKey : exp.keySet()) {
                  String translatedKey = expKey.replace(".", "/");
                  assertTrue(dt.dt.containsKey(translatedKey));
                  assertTrue(dt.dt.get(translatedKey).size() == exp.get(expKey).size());
               }
            }

         }
      }
   }
}

