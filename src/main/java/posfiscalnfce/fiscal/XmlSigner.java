package posfiscalnfce.fiscal;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.crypto.dsig.CanonicalizationMethod;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.SignatureMethod;
import javax.xml.crypto.dsig.Transform;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.dom.DOMSignContext;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;
import javax.xml.crypto.dsig.spec.C14NMethodParameterSpec;
import javax.xml.crypto.dsig.spec.TransformParameterSpec;
import java.util.HexFormat;
import java.util.List;

/**
 * Assinatura XMLDSig "enveloped" do grupo infNFe, como exige o Manual de Orientação do
 * Contribuinte: C14N, RSA-SHA1 e referência ao atributo Id de infNFe.
 */
@Component
public class XmlSigner {

    public record SignedXml(Document document, String digestValueHex) {
    }

    private final SigningKeyProvider keys;

    public XmlSigner(SigningKeyProvider keys) {
        this.keys = keys;
    }

    public SignedXml sign(Document document) {
        try {
            var infNFe = (Element) document.getElementsByTagNameNS(NfceXmlBuilder.NS, "infNFe").item(0);
            infNFe.setIdAttribute("Id", true);
            var factory = XMLSignatureFactory.getInstance("DOM");
            var reference = factory.newReference("#" + infNFe.getAttribute("Id"),
                    factory.newDigestMethod(DigestMethod.SHA1, null),
                    List.of(factory.newTransform(Transform.ENVELOPED, (TransformParameterSpec) null),
                            factory.newTransform(CanonicalizationMethod.INCLUSIVE, (TransformParameterSpec) null)),
                    null, null);
            var signedInfo = factory.newSignedInfo(
                    factory.newCanonicalizationMethod(CanonicalizationMethod.INCLUSIVE, (C14NMethodParameterSpec) null),
                    factory.newSignatureMethod(SignatureMethod.RSA_SHA1, null),
                    List.of(reference));

            var key = keys.key();
            var keyInfoFactory = factory.getKeyInfoFactory();
            KeyInfo keyInfo = key.certificate() != null
                    ? keyInfoFactory.newKeyInfo(List.of(keyInfoFactory.newX509Data(List.of(key.certificate()))))
                    : keyInfoFactory.newKeyInfo(List.of(keyInfoFactory.newKeyValue(key.publicKey())));

            // Signature entra como último filho de NFe; infNFeSupl é inserido antes dela depois,
            // porque o QR Code offline depende do DigestValue calculado aqui.
            var context = new DOMSignContext(key.privateKey(), infNFe.getParentNode());
            factory.newXMLSignature(signedInfo, keyInfo).sign(context);
            return new SignedXml(document, HexFormat.of().formatHex(reference.getDigestValue()));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao assinar o XML", e);
        }
    }
}
