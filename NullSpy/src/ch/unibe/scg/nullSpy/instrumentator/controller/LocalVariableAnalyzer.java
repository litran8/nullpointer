package ch.unibe.scg.nullSpy.instrumentator.controller;

import java.util.ArrayList;
import java.util.HashMap;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.bytecode.BadBytecode;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.InstructionPrinter;
import javassist.bytecode.LineNumberAttribute;
import javassist.bytecode.LocalVariableAttribute;
import javassist.bytecode.Mnemonic;
import javassist.bytecode.Opcode;
import ch.unibe.scg.nullSpy.instrumentator.model.LocalVar;
import ch.unibe.scg.nullSpy.instrumentator.model.LocalVarKey;
import ch.unibe.scg.nullSpy.instrumentator.model.Variable;
import ch.unibe.scg.nullSpy.run.MainProjectModifier;

/**
 * Instruments test-code after locVars.
 * 
 * @author Lina Tran
 *
 */
public class LocalVariableAnalyzer extends VariableAnalyzer implements Opcode {

	private ArrayList<Variable> localVarList;
	private HashMap<LocalVarKey, LocalVar> localVarMap;

	public LocalVariableAnalyzer(CtClass cc, ArrayList<Variable> localVarList,
			HashMap<LocalVarKey, LocalVar> localVarMap) {
		super(cc);
		this.localVarList = localVarList;
		this.localVarMap = localVarMap;
	}

	/**
	 * Checks all locVars in a class and instrument test-code after their
	 * assignments.
	 * 
	 * @throws BadBytecode
	 * @throws CannotCompileException
	 * @throws NotFoundException
	 */
	public void instrumentAfterLocVarAssignment() throws BadBytecode,
			CannotCompileException, NotFoundException {

		// if (method.getName().equals("elementStarted"))
		instrumentAfterLocVarObject(cc.getDeclaredConstructors());
		instrumentAfterLocVarObject(cc.getDeclaredMethods());
		// }
	}

	/**
	 * Searches only locVar which are objects and directly instrument test-code.
	 * 
	 * @param method
	 * @param codeIterator
	 * @param localVariableList
	 * @param lineNumberMap
	 * @param exceptionTable
	 * @throws BadBytecode
	 * @throws CannotCompileException
	 * @throws NotFoundException
	 */
	private void instrumentAfterLocVarObject(CtBehavior[] behaviorList)
			throws BadBytecode, CannotCompileException, NotFoundException {

		for (CtBehavior behavior : behaviorList) {
			// if (method.getName().equals("initManager")) {
			CodeAttribute codeAttr = behavior.getMethodInfo()
					.getCodeAttribute();

			if (codeAttr == null) {
				continue;
			}

			storeParameterData(behavior);

			LocalVariableAttribute localVarAttr = (LocalVariableAttribute) codeAttr
					.getAttribute(LocalVariableAttribute.tag);

			int localVarAttrLength = localVarAttr.tableLength();
			if (localVarAttrLength == 0) {
				continue;
			}

			ArrayList<LocalVarAttrEntry> localVarAttrAsList = getLocalVarAttrAsList(localVarAttr);

			LineNumberAttribute lineNrAttr = (LineNumberAttribute) codeAttr
					.getAttribute(LineNumberAttribute.tag);

			CodeIterator codeIter = codeAttr.iterator();
			codeIter.begin();

			ArrayList<Integer> instrPositions = new ArrayList<Integer>();

			int methodMaxPc = lineNrAttr.startPc(lineNrAttr.tableLength() - 1);

			while (codeIter.hasNext()) {

				int pos = codeIter.next();
				instrPositions.add(pos);

				int op = codeIter.byteAt(pos);

				if (isLocVarObject(op) && pos <= methodMaxPc) {

					int startPos = getStartPos(behavior, pos);
					int afterPos = codeIter.next();

					int localVarAttrIndex = getLocalVarAttrIndex(codeIter,
							localVarAttrAsList, pos, "astore.*");

					// Printer p = new Printer();
					// p.printMethod(method, 0);

					int localVarSlot = localVarAttrAsList
							.get(localVarAttrIndex).index;

					String instr = InstructionPrinter.instructionString(
							codeIter, pos, codeAttr.getConstPool());

					if (instr.contains(" ")) {
						instr = instr.substring(instr.indexOf(" ") + 1);
					} else {
						instr = instr.substring(instr.indexOf("_") + 1);
					}

					int instrSlot = Integer.parseInt(instr);

					String localVarName = "";
					String localVarType = "";
					String varID = "localVariable_";

					// lineNr
					int localVarLineNr = lineNrAttr.toLineNumber(pos);

					if (localVarSlot != instrSlot) {
						varID += instrSlot;
					} else {
						localVarName = localVarAttrAsList
								.get(localVarAttrIndex).varName;
						localVarType = localVarAttrAsList
								.get(localVarAttrIndex).varType;
						varID += localVarSlot;
					}

					// create localVar
					LocalVar localVar = new LocalVar(varID, localVarName,
							localVarLineNr, localVarType, pos, startPos,
							afterPos, cc, behavior, localVarAttrIndex,
							localVarSlot);

					// save localVar into list
					localVarList.add(localVar);

					// hashMap
					localVarMap.put(new LocalVarKey(localVarName, cc.getName(),
							behavior.getName(), behavior.getSignature()),
							localVar);

					// change byteCode
					adaptByteCode(localVar);

					// update codeAttr, codeIter; set codeIter to last
					// checked
					// pos and iterate further
					codeAttr = behavior.getMethodInfo().getCodeAttribute();
					codeIter = codeAttr.iterator();
					codeIter.move(afterPos);

					// update statement for if() and othe stuffs
					methodMaxPc = lineNrAttr
							.startPc(lineNrAttr.tableLength() - 1);
					lineNrAttr = (LineNumberAttribute) codeAttr
							.getAttribute(LineNumberAttribute.tag);
					localVarAttrAsList = getLocalVarAttrAsList(localVarAttr);

					// Printer p = new Printer();
					// System.out.println("Method: " +
					// method.getName());
					// System.out
					// .println("MethodParams: " +
					// method.getSignature());
					// p.printMethod(method, 0);
					// System.out.println();
				}
			}

			// calculates the time modified project uses
			if (behavior.getName().equals("main"))
				addTimeToModifiedProject(behavior);

			// Printer p = new Printer();
			// System.out.println("Method: " + method.getName());
			// System.out.println("MethodParams: " +
			// method.getSignature());
			// p.printMethod(method, 0);
			// System.out.println();

			// }
		}

	}

	private void storeParameterData(CtBehavior behavior)
			throws NotFoundException, BadBytecode, CannotCompileException {
		// TODO Auto-generated method stub
		CodeAttribute codeAttr = behavior.getMethodInfo().getCodeAttribute();
		LocalVariableAttribute localVarAttr = (LocalVariableAttribute) codeAttr
				.getAttribute(LocalVariableAttribute.tag);
		String behaviorName = behavior.getName();

		int behaviorParamAmount = behavior.getParameterTypes().length;
		if (localVarAttr.tableLength() == 0 || behaviorParamAmount == 0
				|| behaviorName.equals("<clinit>")
				|| behaviorName.contains("$"))
			return;

		boolean isBehaviorStatic = Modifier.isStatic(behavior.getModifiers());

		int startIndex = isBehaviorStatic ? 0 : 1;
		if (!isBehaviorStatic)
			behaviorParamAmount += 1;

		for (int i = startIndex; i < behaviorParamAmount; i++) {

			int varSlot = localVarAttr.index(i);
			String varName = localVarAttr.variableName(i);
			String varType = localVarAttr.signature(i);
			if (!(varType.startsWith("[L") && varType.startsWith("L")))
				return;
			String varID = "localVariable_" + varSlot;
			int varLineNr = behavior.getMethodInfo().getLineNumber(0);

			// create localVar
			LocalVar localVar = new LocalVar(varID, varName, varLineNr,
					varType, 0, 0, 0, cc, behavior, varSlot, varSlot);

			// save localVar into list
			localVarList.add(localVar);

			// hashMap
			localVarMap.put(
					new LocalVarKey(varName, cc.getName(), behavior.getName(),
							behavior.getSignature()), localVar);

			// change byteCode
			adaptByteCode(localVar);

			// StringBuilder sb = new StringBuilder();
			// sb.append("ch.unibe.scg.nullSpy.runtimeSupporter.NullDisplayer(");
			// sb.append("\"" + cc.getName() + "\"");
			// sb.append(",");
			// sb.append("\"" + behavior.getName() + "\"");
			// sb.append(",");
			// sb.append("\"" + behavior.getSignature() + "\"");
			// sb.append(",");
			// sb.append("\"" + varID + "\"");
			// sb.append(",");
			// sb.append("\"" + varName + "\"");
			// sb.append(",");
			// sb.append("\"" + varType + "\"");
			// sb.append(",");
			// sb.append(varName);
			// sb.append(",");
			// sb.append(varSlot);
			// sb.append(",");
			// sb.append(varLineNr);
			// sb.append(",");
			// sb.append(0);
			// sb.append(",");
			// sb.append(0);
			// sb.append(",");
			// sb.append(0);
			// sb.append(");");
			// String s = sb.toString();
			// behavior.insertBefore(s);

		}

	}

	/**
	 * Checks if the locVar is an object, NOT a primitive one.
	 * 
	 * @param op
	 * @return
	 */
	private static boolean isLocVarObject(int op) {
		return Mnemonic.OPCODE[op].matches("astore.*");
		// return Mnemonic.OPCODE[op].matches("a{1,2}store.*");

	}

	private int getStartPos(CtBehavior behavior, int pos) throws BadBytecode {
		int res = 0;

		CodeAttribute codeAttr = behavior.getMethodInfo().getCodeAttribute();
		CodeIterator iter = codeAttr.iterator();

		LineNumberAttribute lineNrAttr = (LineNumberAttribute) codeAttr
				.getAttribute(LineNumberAttribute.tag);
		int line = lineNrAttr.toLineNumber(pos);
		res = lineNrAttr.toStartPc(line);

		if (localVarList.size() != 0) {
			Variable lastVar = localVarList.get(localVarList.size() - 1);

			if (isSameBehavior(behavior, lastVar)) {

				iter.move(lastVar.getStorePos());
				iter.next();
				int nextPosAfterLastVar = iter.next();

				if (iter.hasNext() && nextPosAfterLastVar == res) {
					int op = iter.byteAt(res);
					String instr = Mnemonic.OPCODE[op];
					if (instr.matches("ldc.*")) {

						while (iter.hasNext()
								&& !instr.matches("invokestatic.*")) {
							nextPosAfterLastVar = iter.next();
							op = iter.byteAt(nextPosAfterLastVar);
							instr = Mnemonic.OPCODE[op];
						}
						res = iter.next();
					}
				}
			}
		}

		return res;
	}

	private boolean isSameBehavior(CtBehavior currentBehavior, Variable lastVar) {
		boolean inSameBehavior = false;

		CtBehavior lastBehavior = lastVar.getBehavior();

		if (!(lastBehavior == null)) {

			// check class, methodName, methodParams
			inSameBehavior = currentBehavior.getName().equals(
					lastBehavior.getName())
					&& currentBehavior.getDeclaringClass().getName()
							.equals(lastVar.getClassWhereVarIsUsed().getName())
					&& currentBehavior.getSignature().equals(
							lastBehavior.getSignature());
		}
		return inSameBehavior;
	}

	private void addTimeToModifiedProject(CtBehavior method)
			throws CannotCompileException, NotFoundException {

		StringBuilder sb = new StringBuilder();

		sb.append("{");

		sb.append("StackTraceElement[] stElem = $e.getStackTrace();");
		sb.append("ch.unibe.scg.nullSpy.runtimeSupporter.DataMatcher.printLocationOnMatch");
		sb.append("(");
		sb.append("\"" + MainProjectModifier.csvPath + "\"");
		// sb.append("\"" + "C:\\\\Users\\\\Lina Tran\\\\Desktop\\\\VarData.csv"
		// + "\""); // testLine
		sb.append(",");
		sb.append("ch.unibe.scg.nullSpy.runtimeSupporter.NullDisplayer.getLocalVarMap()");
		sb.append(",");
		sb.append("ch.unibe.scg.nullSpy.runtimeSupporter.NullDisplayer.getFieldMap()");
		sb.append(",");
		sb.append("stElem[0].getClassName()");
		sb.append(",");
		sb.append("stElem[0].getLineNumber()");
		sb.append(",");
		sb.append("stElem[0].getMethodName()");
		sb.append(");");

		// sb.append("System.out.println(stElem[0].getClassName());");
		// sb.append("System.out.println(stElem[0].getLineNumber());");
		// sb.append("System.out.println(stElem[0].getMethodName());");
		sb.append("System.out.println($e); throw $e;");

		sb.append("}");

		CtClass etype = ClassPool.getDefault().get("java.lang.Throwable");
		method.addCatch(sb.toString(), etype);

	}

}