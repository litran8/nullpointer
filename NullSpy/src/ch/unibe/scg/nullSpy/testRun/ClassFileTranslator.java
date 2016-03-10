package ch.unibe.scg.nullSpy.testRun;

import java.util.HashMap;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.NotFoundException;
import javassist.Translator;
import ch.unibe.scg.nullSpy.instrumentator.controller.ClassAdapter;

public class ClassFileTranslator implements Translator {

	private ClassAdapter iter = ClassAdapter.getInstance();
	private HashMap<String, CtClass> analyzedClasses = new HashMap<String, CtClass>();

	@Override
	public void start(ClassPool pool) throws NotFoundException,
			CannotCompileException {
		// TODO Auto-generated method stub

	}

	@Override
	public void onLoad(ClassPool pool, String className)
			throws NotFoundException, CannotCompileException {
		if (!className
				.equalsIgnoreCase("ch.unibe.scg.nullSpy.runtimeSupporter.NullDisplayer")
				&& !className
						.equalsIgnoreCase("ch.unibe.scg.nullSpy.runtimeSupporter.Field")
				&& !className
						.equalsIgnoreCase("ch.unibe.scg.nullSpy.runtimeSupporter.LocVar")) {

			CtClass cc;
			if (analyzedClasses.containsKey(className))
				return;
			else {
				cc = ClassPool.getDefault().get(className);
				analyzedClasses.put(cc.getName(), cc);
			}

			cc.stopPruning(true);

			try {
				iter.instrumentCodeAfterFieldLocVarAssignment(cc);
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}
}