/*
 * Copyright 2011 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.marshalling.rebind;

import com.google.gwt.json.client.JSONValue;
import org.jboss.errai.codegen.framework.Cast;
import org.jboss.errai.codegen.framework.Context;
import org.jboss.errai.codegen.framework.Parameter;
import org.jboss.errai.codegen.framework.Statement;
import org.jboss.errai.codegen.framework.builder.AnonymousClassStructureBuilder;
import org.jboss.errai.codegen.framework.builder.BlockBuilder;
import org.jboss.errai.codegen.framework.builder.ClassStructureBuilder;
import org.jboss.errai.codegen.framework.builder.ConstructorBlockBuilder;
import org.jboss.errai.codegen.framework.meta.MetaClass;
import org.jboss.errai.codegen.framework.meta.MetaClassFactory;
import org.jboss.errai.codegen.framework.meta.impl.build.BuildMetaClass;
import org.jboss.errai.codegen.framework.util.Bool;
import org.jboss.errai.codegen.framework.util.GenUtil;
import org.jboss.errai.codegen.framework.util.Stmt;
import org.jboss.errai.common.client.api.annotations.ExposeEntity;
import org.jboss.errai.common.client.api.annotations.Portable;
import org.jboss.errai.common.metadata.RebindUtils;
import org.jboss.errai.marshalling.client.api.Marshaller;
import org.jboss.errai.marshalling.client.api.MarshallerFactory;
import org.jboss.errai.marshalling.client.api.MarshallingSession;
import org.jboss.errai.marshalling.rebind.api.ArrayMarshallerCallback;
import org.jboss.errai.marshalling.rebind.api.GeneratorMappingContext;
import org.jboss.errai.marshalling.rebind.api.MappingStrategy;
import org.jboss.errai.marshalling.rebind.util.MarshallingGenUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.util.TypeLiteral;
import java.io.File;
import java.lang.annotation.Annotation;
import java.util.*;

import static org.jboss.errai.codegen.framework.meta.MetaClassFactory.parameterizedAs;
import static org.jboss.errai.codegen.framework.meta.MetaClassFactory.typeParametersOf;
import static org.jboss.errai.codegen.framework.util.Implementations.*;
import static org.jboss.errai.codegen.framework.util.Stmt.loadVariable;
import static org.jboss.errai.marshalling.rebind.util.MarshallingGenUtil.getVarName;

/**
 * @author Mike Brock <cbrock@redhat.com>
 */
public class MarshallerGeneratorFactory {
  private static final String MARSHALLERS_VAR = "marshallers";

  private GeneratorMappingContext mappingContext;

  ClassStructureBuilder<?> classStructureBuilder;
  ConstructorBlockBuilder<?> constructor;
  Context classContext;

  Set<String> arrayMarshallers = new HashSet<String>();

  private Logger log = LoggerFactory.getLogger(MarshallerGeneratorFactory.class);

  long startTime;

  public String generate(String packageName, String clazzName) {
    File fileCacheDir = RebindUtils.getErraiCacheDir();
    File cacheFile = new File(fileCacheDir.getAbsolutePath() + "/" + clazzName + ".java");

    final Set<Class<? extends Annotation>> annos = new HashSet<Class<? extends Annotation>>();
    annos.add(Portable.class);
    annos.add(ExposeEntity.class);

    String gen;
    if (RebindUtils.hasClasspathChangedForAnnotatedWith(annos) || !cacheFile.exists()) {
      log.info("generating marshalling class...");
      long st = System.currentTimeMillis();
      gen = _generate(packageName, clazzName);
      log.info("generated marshalling class in " + (System.currentTimeMillis() - st) + "ms");

      if (Boolean.getBoolean("errai.marshalling.printOut")) {
        System.out.println(gen);
      }

      RebindUtils.writeStringToFile(cacheFile, gen);
    }
    else {
      gen = RebindUtils.readFileToString(cacheFile);
      log.info("nothing has changed. using cached marshaller factory class.");
    }

    return gen;
  }

  private String _generate(String packageName, String clazzName) {
    startTime = System.currentTimeMillis();

    classStructureBuilder = implement(MarshallerFactory.class, packageName, clazzName);
    classContext = ((BuildMetaClass) classStructureBuilder.getClassDefinition()).getContext();
    mappingContext = new GeneratorMappingContext(classContext, classStructureBuilder.getClassDefinition(),
            classStructureBuilder, new ArrayMarshallerCallback() {
      @Override
      public Statement marshal(MetaClass type, Statement value) {
        createDemarshallerIfNeeded(type);
        return value;
      }

      @Override
      public Statement demarshall(MetaClass type, Statement value) {
        String variable = createDemarshallerIfNeeded(type);

        value = Stmt.loadVariable(getVarName(List.class)).invoke("demarshall", value, Stmt.loadVariable("a1"));

        return Stmt.loadVariable(variable).invoke("demarshall", value, Stmt.loadVariable("a1"));
      }

      private String createDemarshallerIfNeeded(MetaClass type) {
        return addArrayMarshaller(type);
      }
    });

    //loadMarshallers();

    MetaClass javaUtilMap = MetaClassFactory.get(
            new TypeLiteral<Map<String, Marshaller>>() {
            }
    );

    autoInitializedField(classStructureBuilder, javaUtilMap, MARSHALLERS_VAR, HashMap.class);

    constructor = classStructureBuilder.publicConstructor();

    for (Class<?> cls : mappingContext.getDefinitionsFactory().getExposedClasses()) {
      String clsName = cls.getName();

      if (!mappingContext.getDefinitionsFactory().hasDefinition(clsName)) {
        continue;
      }

      Class<? extends Marshaller> marshallerCls = mappingContext.getDefinitionsFactory().getDefinition(clsName)
              .getClientMarshallerClass();

      if (marshallerCls == null) {
        continue;
      }

      String varName = getVarName(clsName);

      classStructureBuilder.privateField(varName, marshallerCls).finish();

      constructor.append(Stmt.create(classContext)
              .loadVariable(varName).assignValue(Stmt.newObject(marshallerCls)));

      constructor.append(Stmt.create(classContext).loadVariable(MARSHALLERS_VAR)
              .invoke("put", clsName, loadVariable(varName)));

      for (Map.Entry<String, String> aliasEntry : mappingContext.getDefinitionsFactory().getMappingAliases().entrySet()) {
        if (aliasEntry.getValue().equals(clsName)) {
          constructor.append(Stmt.create(classContext).loadVariable(MARSHALLERS_VAR)
                  .invoke("put", aliasEntry.getKey(), loadVariable(varName)));
        }
      }
    }

    generateMarshallers();

    classStructureBuilder.publicMethod(Marshaller.class, "getMarshaller").parameters(String.class, String.class)
            .body()
            .append(loadVariable(MARSHALLERS_VAR).invoke("get", loadVariable("a1")).returnValue())
            .finish();

    // special support for Object[]
    addArrayMarshaller(MetaClassFactory.get(Object[].class));

    return classStructureBuilder.toJavaString();
  }

  private void generateMarshallers() {
    final Set<Class<?>> exposed = mappingContext.getDefinitionsFactory().getExposedClasses();

    for (Class<?> clazz : exposed) {
      mappingContext.registerGeneratedMarshaller(clazz.getName());
    }

    for (Class<?> clazz : exposed) {
      if (clazz.isEnum()) continue;
      
      if (mappingContext.getDefinitionsFactory().getDefinition(clazz).getClientMarshallerClass() != null) {
        continue;
      }

      MetaClass metaClazz = MetaClassFactory.get(clazz);
      Statement marshaller = marshal(metaClazz);
      MetaClass type = marshaller.getType();
      String varName = getVarName(clazz);

      classStructureBuilder.privateField(varName, type).finish();

      constructor.append(loadVariable(varName).assignValue(marshaller));

      constructor.append(Stmt.create(classContext).loadVariable(MARSHALLERS_VAR)
              .invoke("put", clazz.getCanonicalName(), loadVariable(varName)));


      for (Map.Entry<String, String> aliasEntry : mappingContext.getDefinitionsFactory().getMappingAliases().entrySet()) {
        if (aliasEntry.getValue().equals(clazz.getName())) {
          constructor.append(Stmt.create(classContext).loadVariable(MARSHALLERS_VAR)
                  .invoke("put", aliasEntry.getKey(), loadVariable(varName)));
        }
      }
    }

    constructor.finish();
  }

  private Statement marshal(MetaClass cls) {
    MappingStrategy strategy = MappingStrategyFactory.createStrategy(mappingContext, cls);
    if (strategy == null) {
      throw new RuntimeException("no available marshaller for class: " + cls.getName());
    }
    return strategy.getMapper().getMarshaller();
  }

  private String addArrayMarshaller(MetaClass type) {
    String varName = getVarName(type);

    if (!arrayMarshallers.contains(varName)) {
      classStructureBuilder.privateField(varName,
              parameterizedAs(Marshaller.class, typeParametersOf(List.class, type))).finish();
      Statement marshaller = generateArrayMarshaller(type);
      constructor.append(loadVariable(varName).assignValue(marshaller));

      constructor.append(Stmt.create(classContext).loadVariable(MARSHALLERS_VAR)
              .invoke("put", type.getFullyQualifiedName(), loadVariable(varName)));

      arrayMarshallers.add(varName);
    }

    return varName;
  }

  private Statement generateArrayMarshaller(MetaClass arrayType) {
    MetaClass toMap = arrayType;
    while (toMap.isArray()) {
      toMap = toMap.getComponentType();
    }
    int dimensions = GenUtil.getArrayDimensions(arrayType);

    AnonymousClassStructureBuilder classStructureBuilder
            = Stmt.create(mappingContext.getCodegenContext())
            .newObject(parameterizedAs(Marshaller.class, typeParametersOf(List.class, arrayType))).extend();

    MetaClass anonClass = classStructureBuilder.getClassDefinition();

    classStructureBuilder.publicOverridesMethod("getTypeHandled")
            .append(Stmt.load(toMap).returnValue())
            .finish();

    classStructureBuilder.publicOverridesMethod("getEncodingType")
            .append(Stmt.load("json").returnValue())
            .finish();

    BlockBuilder<?> bBuilder = classStructureBuilder.publicOverridesMethod("demarshall",
            Parameter.of(Object.class, "a0"), Parameter.of(MarshallingSession.class, "a1"));

    bBuilder.append(
            Stmt.if_(Bool.isNull(loadVariable("a0")))
                    .append(Stmt.load(null).returnValue())
                    .finish()
                    .else_()
                    .append(Stmt.invokeStatic(anonClass, "_demarshall" + dimensions,
                            loadVariable("a0"), loadVariable("a1")).returnValue())
                    .finish());
    bBuilder.finish();

    arrayDemarshallCode(toMap, dimensions, classStructureBuilder);

    BlockBuilder<?> marshallMethodBlock = classStructureBuilder.publicOverridesMethod("marshall",
            Parameter.of(Object.class, "a0"), Parameter.of(MarshallingSession.class, "a1"));

    marshallMethodBlock.append(
            Stmt.if_(Bool.isNull(loadVariable("a0")))
                    .append(Stmt.load(null).returnValue())
                    .finish()
                    .else_()
                    .append(Stmt.invokeStatic(anonClass, "_marshall" + dimensions,
                            loadVariable("a0"), loadVariable("a1")).returnValue())
                    .finish()
    );

    classStructureBuilder.publicOverridesMethod("handles", Parameter.of(JSONValue.class, "a0"))
            .append(Stmt.load(true).returnValue())
            .finish();

    marshallMethodBlock.finish();

    return classStructureBuilder.finish();
  }

  private void arrayDemarshallCode(MetaClass toMap, int dim, AnonymousClassStructureBuilder anonBuilder) {
    Object[] dimParms = new Object[dim];
    dimParms[0] = Stmt.loadVariable("a0").invoke("size");

    final MetaClass arrayType = toMap.asArrayOf(dim);

    MetaClass outerType = toMap.getOuterComponentType();
    if (!outerType.isArray() && outerType.isPrimitive()) {
      outerType = outerType.asBoxed();
    }

    Statement outerAccessorStatement =
            loadVariable("newArray", loadVariable("i"))
                    .assignValue(Cast.to(outerType,
                            loadVariable("a0").invoke("get", loadVariable("i"))));


    final BlockBuilder<?> dmBuilder =
            anonBuilder.privateMethod(arrayType, "_demarshall" + dim)
                    .parameters(List.class, MarshallingSession.class).body();

    dmBuilder.append(Stmt
            .declareVariable(arrayType).named("newArray")
            .initializeWith(Stmt.newArray(toMap, dimParms)));

    dmBuilder.append(autoForLoop("i", Stmt.loadVariable("newArray").loadField("length"))
            .append(dim == 1 ? outerAccessorStatement
                    : loadVariable("newArray", loadVariable("i")).assignValue(
                    Stmt.invokeStatic(anonBuilder.getClassDefinition(),
                            "_demarshall" + (dim - 1),
                            Cast.to(List.class, Stmt.loadVariable("a0").invoke("get", Stmt.loadVariable("i"))),
                            Stmt.loadVariable("a1"))))

            .finish())
            .append(Stmt.loadVariable("newArray").returnValue());


    dmBuilder.finish();

    final BlockBuilder<?> mBuilder = anonBuilder.privateMethod(String.class, "_marshall" + dim)
            .parameters(arrayType, MarshallingSession.class).body();

    mBuilder.append(Stmt.declareVariable(StringBuilder.class).named("sb")
            .initializeWith(Stmt.newObject(StringBuilder.class).withParameters("[")))
            .append(autoForLoop("i", Stmt.loadVariable("a0").loadField("length"))
                    .append(Stmt.if_(Bool.greaterThan(Stmt.loadVariable("i"), 0))
                            .append(Stmt.loadVariable("sb").invoke("append", ",")).finish())
                    .append(Stmt.loadVariable("sb").invoke("append", dim == 1 ?
                            Stmt.loadVariable(MarshallingGenUtil.getVarName(outerType))
                                    .invoke("marshall",
                                            Stmt.loadVariable("a0", Stmt.loadVariable("i")),
                                            Stmt.loadVariable("a1"))
                            :
                            Stmt.invokeStatic(anonBuilder.getClassDefinition(),
                                    "_marshall" + (dim - 1), Stmt.loadVariable("a0", Stmt.loadVariable("i")), loadVariable("a1"))))
                    .finish())
            .append(Stmt.loadVariable("sb").invoke("append", "]").invoke("toString").returnValue())
            .finish();

    if (dim > 1) {
      arrayDemarshallCode(toMap, dim - 1, anonBuilder);
    }
  }

//  private void loadMarshallers() {
//    Set<Class<?>> marshallers =
//            ScannerSingleton.getOrCreateInstance().getTypesAnnotatedWith(ClientMarshaller.class);
//
//    for (Class<?> cls : marshallers) {
//      if (Marshaller.class.isAssignableFrom(cls)) {
//        try {
//          Class<?> type = (Class<?>) Marshaller.class.getMethod("getTypeHandled").invoke(cls.newInstance());
//          mappingContext.registerMarshaller(type.getName(), cls.asSubclass(Marshaller.class));
//
//          if (cls.isAnnotationPresent(ImplementationAliases.class)) {
//            for (Class<?> c : cls.getAnnotation(ImplementationAliases.class).value()) {
//              mappingContext.registerMappingAlias(c, type);
//            }
//          }
//        }
//        catch (Throwable t) {
//          throw new RuntimeException("could not instantiate marshaller class: " + cls.getName(), t);
//        }
//      }
//      else {
//        throw new RuntimeException("class annotated with " + ClientMarshaller.class.getCanonicalName()
//                + " does not implement " + Marshaller.class.getName());
//      }
//    }
//  }
}