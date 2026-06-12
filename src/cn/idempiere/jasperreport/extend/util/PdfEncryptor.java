package cn.idempiere.jasperreport.extend.util;

import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class PdfEncryptor {

    /**
     * Crypte un PDF avec mot de passe utilisateur et proprietaire 
     *
     * @param sourcePdf     fichier PDF source
     * @param userPassword  mot de passe pour ouverture
     * @param ownerPassword mot de passe propriétaire (permissions)
     * @return File         le fichier PDF crypté
     * @throws IOException
     * @throws DocumentException
     */
    public static File encryptPdf(File sourcePdf, String userPassword, String ownerPassword)
            throws IOException, DocumentException {

        if (sourcePdf == null || !sourcePdf.exists()) {
            throw new IllegalArgumentException("Fichier source introuvable : " + sourcePdf);
        }

        // Generer le fichier de sortie dans le meme dossier avec suffixe "_encrypted.pdf"
        String encryptedPath = sourcePdf.getParent() + File.separator +
                sourcePdf.getName().replace(".pdf", "_encryptedByIsnov.pdf");
        File targetPdf = new File(encryptedPath);

        PdfReader reader = new PdfReader(sourcePdf.getAbsolutePath());
        PdfStamper stamper = new PdfStamper(reader, new FileOutputStream(targetPdf));

        // Crypter le PDF avec AES 128 et permissions d'impression
        stamper.setEncryption(
                userPassword.getBytes(),
                ownerPassword.getBytes(),
                PdfWriter.ALLOW_PRINTING,    // permissions
                PdfWriter.ENCRYPTION_AES_256   // type de cryptage
        );
        stamper.close();
        reader.close();

        return targetPdf;
    }
}
