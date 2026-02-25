package org.zkoss.zkidea.reference;

import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiParameterList;
import org.zkoss.bind.annotation.Command;
import org.zkoss.bind.annotation.GlobalCommand;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

/**
 * Builds a {@link PsiClass} mock from a real Java class via reflection so that
 * tests can define the ViewModel in plain Java rather than hand-wiring PSI mocks.
 *
 * <p>{@link #from(Class)} traverses the class hierarchy (stopping at {@link Object})
 * and includes every declared method and constructor, mirroring the contract of
 * {@code PsiClass.getAllMethods()}.  ZK binding annotations ({@link Command},
 * {@link GlobalCommand}) are read from the test-stub annotations in
 * {@code org.zkoss.bind.annotation} and translated into mocked {@link PsiAnnotation}
 * objects with the correct qualified names.
 *
 * <p>All stubs are registered with {@code lenient()} so that unused stubs in a
 * specific test do not trigger Mockito's strict-stubbing violations.
 */
class PsiClassMocker {

    private static final String CMD_FQN  = "org.zkoss.bind.annotation.Command";
    private static final String GCMD_FQN = "org.zkoss.bind.annotation.GlobalCommand";

    /**
     * Creates a mock {@link PsiClass} whose {@code getAllMethods()} mirrors all
     * methods declared in {@code javaClass} and its superclasses (excluding
     * {@link Object}), plus the declared constructors of {@code javaClass}.
     */
    static PsiClass from(Class<?> javaClass) {
        List<PsiMethod> psiMethods = new ArrayList<>();

        // Walk the class hierarchy, excluding Object itself
        Class<?> c = javaClass;
        while (c != null && c != Object.class) {
            for (Method m : c.getDeclaredMethods()) {
                psiMethods.add(mockMethod(m));
            }
            c = c.getSuperclass();
        }

        // Constructors from the target class only
        for (Constructor<?> ctor : javaClass.getDeclaredConstructors()) {
            psiMethods.add(mockConstructor(ctor));
        }

        PsiClass psiClass = mock(PsiClass.class);
        lenient().when(psiClass.getAllMethods())
                 .thenReturn(psiMethods.toArray(new PsiMethod[0]));
        return psiClass;
    }

    private static PsiMethod mockMethod(Method m) {
        // Build annotations BEFORE any when() call that uses the result —
        // calling mock helpers inside thenReturn() causes UnfinishedStubbingException
        // because Mockito's stub recorder is already active for the outer when().
        PsiAnnotation[] annotations = buildAnnotations(m);

        PsiMethod pm = mock(PsiMethod.class);
        lenient().when(pm.getName()).thenReturn(m.getName());
        lenient().when(pm.hasModifierProperty(PsiModifier.PUBLIC))
                 .thenReturn(Modifier.isPublic(m.getModifiers()));
        lenient().when(pm.isConstructor()).thenReturn(false);

        PsiParameterList pl = mock(PsiParameterList.class);
        lenient().when(pl.getParametersCount()).thenReturn(m.getParameterCount());
        lenient().when(pm.getParameterList()).thenReturn(pl);
        lenient().when(pm.getAnnotations()).thenReturn(annotations);
        return pm;
    }

    private static PsiMethod mockConstructor(Constructor<?> ctor) {
        PsiMethod pm = mock(PsiMethod.class);
        lenient().when(pm.getName()).thenReturn(ctor.getDeclaringClass().getSimpleName());
        lenient().when(pm.hasModifierProperty(PsiModifier.PUBLIC))
                 .thenReturn(Modifier.isPublic(ctor.getModifiers()));
        lenient().when(pm.isConstructor()).thenReturn(true);

        PsiParameterList pl = mock(PsiParameterList.class);
        lenient().when(pl.getParametersCount()).thenReturn(ctor.getParameterCount());
        lenient().when(pm.getParameterList()).thenReturn(pl);
        lenient().when(pm.getAnnotations()).thenReturn(new PsiAnnotation[0]);
        return pm;
    }

    private static PsiAnnotation[] buildAnnotations(Method m) {
        List<PsiAnnotation> annotations = new ArrayList<>();

        Command cmd = m.getAnnotation(Command.class);
        if (cmd != null) {
            PsiAnnotation ann = mock(PsiAnnotation.class);
            lenient().when(ann.getQualifiedName()).thenReturn(CMD_FQN);
            // @Command has String[] value(); use the first element if present.
            String[] values = cmd.value();
            if (values.length > 0 && !values[0].isEmpty()) {
                PsiAnnotationMemberValue val = mock(PsiAnnotationMemberValue.class);
                // getText() must return the value wrapped in double-quotes,
                // matching the format that ZkCommandReference.getCommandName() strips.
                lenient().when(val.getText()).thenReturn("\"" + values[0] + "\"");
                lenient().when(ann.findDeclaredAttributeValue("value")).thenReturn(val);
            } else {
                lenient().when(ann.findDeclaredAttributeValue("value")).thenReturn(null);
            }
            annotations.add(ann);
        }

        if (m.isAnnotationPresent(GlobalCommand.class)) {
            PsiAnnotation ann = mock(PsiAnnotation.class);
            lenient().when(ann.getQualifiedName()).thenReturn(GCMD_FQN);
            lenient().when(ann.findDeclaredAttributeValue("value")).thenReturn(null);
            annotations.add(ann);
        }

        return annotations.toArray(new PsiAnnotation[0]);
    }
}
