package scala.meta.internal.implementation
import scala.meta.internal.semanticdb.SymbolInformation
import scala.meta.internal.semanticdb.Type
import scala.meta.internal.semanticdb.Scope
import scala.meta.internal.semanticdb.ValueSignature
import scala.meta.internal.semanticdb.Scala._
import scala.meta.internal.semanticdb.Scala.Descriptor
import scala.meta.internal.semanticdb.TypeRef
import scala.meta.internal.semanticdb.MethodSignature
import scala.meta.internal.semanticdb.Signature
import scala.meta.internal.semanticdb.ClassSignature
import scala.meta.internal.semanticdb.ExistentialType

object MethodImplementation {

  import ImplementationProvider._

  def find(
      parentSymbol: SymbolInformation,
      parentClassSymbol: SymbolInformation,
      inheritanceContext: InheritanceContext,
      classLocation: ClassLocation,
      findSymbolInCurrentContext: String => Option[SymbolInformation]
  ): Option[String] = {
    val classSymbolInfo = findSymbolInCurrentContext(classLocation.symbol)

    def createAsSeenFrom(info: SymbolInformation) = {
      classLocation
        .toRealNames(info, translateKey = false)
        .asSeenFromMap
    }

    def isOverridenMethod(
        methodSymbolInfo: SymbolInformation
    )(implicit context: Context): Boolean = {
      (methodSymbolInfo.kind.isField || methodSymbolInfo.kind.isMethod) &&
      methodSymbolInfo.displayName == parentSymbol.displayName &&
      signaturesEqual(parentSymbol.signature, methodSymbolInfo.signature)(
        context
      )
    }

    val validMethods = for {
      symbolInfo <- classSymbolInfo.toIterable
      if symbolInfo.signature.isInstanceOf[ClassSignature]
      classSignature = symbolInfo.signature.asInstanceOf[ClassSignature]
      declarations <- classSignature.declarations.toIterable
      methodSymbol <- declarations.symlinks
      methodSymbolInfo <- findSymbolInCurrentContext(methodSymbol)
      asSeenFrom = createAsSeenFrom(symbolInfo)
      context = Context(
        findSymbolInCurrentContext,
        inheritanceContext.findSymbol,
        asSeenFrom
      )
      if isOverridenMethod(methodSymbolInfo)(context)
    } yield methodSymbol
    validMethods.headOption
  }

  private def symbolsAreEqual(
      symParent: String,
      symChild: String
  )(implicit context: Context) = {
    val dealiasedChild = dealiasClass(symChild, context.findSymbol)
    val dealiasedParent = dealiasClass(symParent, context.findSymbolInParent)
    (dealiasedParent.desc, dealiasedChild.desc) match {
      case (Descriptor.TypeParameter(tp), Descriptor.TypeParameter(tc)) =>
        context.asSeenFrom.getOrElse(tp, tp) == tc
      case (Descriptor.TypeParameter(tp), Descriptor.Type(tc)) =>
        context.asSeenFrom.getOrElse(tp, tp) == tc
      case (Descriptor.Parameter(tp), Descriptor.Parameter(tc)) =>
        tp == tc
      case (Descriptor.Type(tp), Descriptor.Type(tc)) =>
        context.asSeenFrom.getOrElse(tp, tp) == tc
      case _ =>
        false
    }
  }

  private def typesAreEqual(
      typeParent: Type,
      typeChild: Type
  )(implicit context: Context): Boolean = {
    (typeParent, typeChild) match {
      case (tp: TypeRef, tc: TypeRef) =>
        symbolsAreEqual(tp.symbol, tc.symbol)
      case (tp: ExistentialType, tc: ExistentialType) =>
        typesAreEqual(tp.tpe, tc.tpe)
      case _ => false
    }
  }

  private def paramsAreEqual(
      scopesParent: Seq[Scope],
      scopesChild: Seq[Scope]
  )(implicit context: Context): Boolean = {
    scopesParent.size == scopesChild.size &&
    scopesParent.zip(scopesChild).forall {
      case (scopePar, scopeChild) =>
        scopePar.hardlinks.size == scopeChild.hardlinks.size && scopePar.hardlinks
          .zip(scopeChild.hardlinks)
          .forall {
            case (linkPar, linkChil) =>
              signaturesEqual(linkPar.signature, linkChil.signature)
          }
    }
  }

  private def signaturesEqual(
      parentSig: Signature,
      sig: Signature
  )(implicit context: Context): Boolean = {
    (parentSig, sig) match {
      case (sig1: MethodSignature, sig2: MethodSignature) =>
        val newContext = context.addAsSeenFrom(
          typeMappingFromMethodScope(
            sig1.typeParameters,
            sig2.typeParameters
          )
        )
        val returnTypesEqual =
          typesAreEqual(sig1.returnType, sig2.returnType)(newContext)
        lazy val enrichedSig =
          addParameterSignatures(sig2, context.findSymbol)
        returnTypesEqual && paramsAreEqual(
          sig1.parameterLists,
          enrichedSig.parameterLists
        )(newContext)
      case (v1: ValueSignature, v2: ValueSignature) =>
        typesAreEqual(v1.tpe, v2.tpe)
      case _ => false
    }
  }

  private def typeMappingFromMethodScope(
      scopeParent: Option[Scope],
      scopeChild: Option[Scope]
  ): Map[String, String] = {
    val mappings = for {
      scopeP <- scopeParent.toList
      scopeC <- scopeChild.toList
      (typeP, typeC) <- scopeP.symlinks.zip(scopeC.symlinks)
    } yield typeP.desc.name.toString -> typeC.desc.name.toString
    mappings.toMap
  }

  private case class Context(
      findSymbol: String => Option[SymbolInformation],
      findSymbolInParent: String => Option[SymbolInformation],
      asSeenFrom: Map[String, String]
  ) {
    def addAsSeenFrom(asSeenFrom: Map[String, String]) = {
      this.copy(asSeenFrom = this.asSeenFrom ++ asSeenFrom)
    }
  }
}
