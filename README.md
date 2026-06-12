# cn.idempiere.jasperreport.extend

> **Portage iDempiere 7.1 & personnalisation** par **Corneille NGALEU**  
> Concept original : [hieplq](https://github.com/hieplq) · [Wiki original](https://wiki.idempiere.org/en/Extend_Jasper_Engine) · Licence : GPLv2

---

## Ce que fait ce plugin

`cn.idempiere.jasperreport.extend` est un plugin OSGi pour **iDempiere 7.1** qui automatise la génération, le chiffrement et l'envoi par email de rapports JasperReport — un par employé, par période de paie.

**Cas d'usage typique :** En fin de période de paie, lancer un seul processus. Le plugin parcourt tous les `HR_Movement` du processus de paie, génère un bulletin PDF personnalisé, le chiffre avec le mot de passe propre à l'employé, puis l'envoie par email — entièrement automatisé, sans intervention manuelle.

---

## Fonctionnalités

### Génération PDF par employé
- Résout dynamiquement le processus JasperReport via `AD_Process.Value`
- Injecte les paramètres d'exécution : `HR_Movement_ID`, `AD_User_ID`, `AD_Client_ID`, options personnalisées
- Exécute le rapport via `ServerProcessCtl` et récupère le fichier `File` généré

### Chiffrement PDF (AES-256)
- Chiffre chaque PDF avec **deux mots de passe** :
  - **Mot de passe utilisateur** — issu des données du partenaire commercial (`champ Password`, ou `IEXT_emp_matricula + "@-"` en fallback)
  - **Mot de passe propriétaire** — mot de passe admin fixe, contrôle les permissions
- Chiffrement **AES-256** avec permission `ALLOW_PRINTING`
- Fichier de sortie nommé `*_encryptedByIsnov.pdf`
- Propulsé par **iText** (`PdfReader` / `PdfStamper`)

### Envoi par email
- Résolution de l'expéditeur configurable via la clé `MSysConfig` `MAIL_XXX_XXXXXX` :
  - `U` → email de l'utilisateur courant (`AD_User_ID`)
  - `C` → email de demande du client
  - `S` → email du client système
- Destinataire résolu depuis `C_BPartner.EMail` où `iEXT_EMailRecipient = 'Y'`
- Corps et sujet du mail pilotés par le modèle `R_MailText` (Value = `RH_PaySlipOfEmployee`)
- Supporte les formats **texte brut** et **HTML**
- En cas de succès : positionne `iEXT_EmailSent = true` sur l'enregistrement `HR_Movement`

### Compteurs et journalisation
- Retourne un résumé : `"Process terminé. Emails envoyés : X, échecs : Y"`
- Trace `CLogger` détaillée à chaque étape (génération PDF, chiffrement, envoi)

---

## Architecture

```
cn.idempiere.jasperreport.extend
│
├── process/
│   └── SendEmailEncryptedJasperReport.java   ← SvrProcess principal
│       · Parcourt les enregistrements HR_Movement
│       · Construit ProcessInfo + paramètres
│       · Appelle ServerProcessCtl pour lancer Jasper
│       · Déclenche le chiffrement PDF
│       · Envoie le PDF chiffré par email
│
└── util/
    └── PdfEncryptor.java                     ← Utilitaire de chiffrement PDF
        · PdfReader + PdfStamper (iText)
        · AES-256, ALLOW_PRINTING
        · Retourne le fichier chiffré
```

---

## Paramètres du processus

| Paramètre | Type | Description |
|---|---|---|
| `AD_Process_Value` | String | Valeur de l'AD_Process JasperReport à exécuter |
| `isEncrypted` | Boolean | Activer/désactiver le chiffrement PDF |
| `HR_Process_ID` | Integer | Processus de paie à traiter |
| `AD_User_ID` | Integer | Utilisateur courant (utilisé pour la résolution de l'expéditeur) |
| `IsOption1` | Boolean | Option personnalisée transmise au rapport Jasper |
| `IsOption2` | String | Option personnalisée transmise au rapport Jasper |

Paramètres transmis au rapport Jasper :

| Paramètre | Description |
|---|---|
| `isEncrypted` | Indicateur de chiffrement |
| `createPassword` | Mot de passe propriétaire (`adminPassword`) |
| `readPassword` | Mot de passe utilisateur (`userPassword`) |
| `AD_Client_ID` | Identifiant client |
| `HR_Movement_ID` | Identifiant de l'enregistrement mouvement |
| `AD_User_ID` | Identifiant utilisateur |
| `IsOption1` | Option personnalisée 1 |
| `IsOption2` | Option personnalisée 2 |

---

## Prérequis

- iDempiere **7.1**
- PostgreSQL
- Librairie iText disponible dans le classpath OSGi
- Champs personnalisés sur les tables standard :
  - `C_BPartner.iEXT_EMailRecipient` (Y/N) — marque les destinataires email
  - `C_BPartner.Password` — mot de passe utilisateur employé pour le PDF
  - `C_BPartner.IEXT_emp_matricula` — matricule employé (mot de passe de secours)
  - `HR_Movement.iEXT_EmailSent` (Y/N) — suivi des envois
- Enregistrement `R_MailText` avec `Value = 'RH_PaySlipOfEmployee'`
- Clé `MSysConfig` `MAIL_XXX_XXXXXX` positionnée à `U`, `C` ou `S`

---

## Installation

1. **Compiler** avec Eclipse PDE — exporter en JAR bundle OSGi
2. **Déployer** le JAR dans le dossier `plugins/` d'iDempiere ou via la console OSGi
3. **Vérifier** que le bundle est actif :
   ```
   osgi> ss cn.idempiere.jasperreport.extend
   ```
4. **Créer** l'enregistrement `AD_Process` pointant vers `cn.idempiere.jasperreport.extend.process.SendEmailEncryptedJasperReport`
5. **Configurer** les paramètres comme décrit ci-dessus
6. **Définir** la clé `MSysConfig` `MAIL_XXX_XXXXXX` selon la stratégie d'expéditeur souhaitée (`U` / `C` / `S`)
7. **Créer** l'enregistrement `R_MailText` avec Value `RH_PaySlipOfEmployee`

---

## Ce qui a changé par rapport à l'original (6.1 → 7.1)

| Domaine | Modification |
|---|---|
| Dépendances OSGi | Mise à jour du `MANIFEST.MF` pour l'API iDempiere 7.1 |
| Envoi email | Migration vers le pattern `MClient.createEMailFrom()` |
| Chiffrement PDF | Nouvel utilitaire `PdfEncryptor` avec iText AES-256 |
| Intégration RH | Boucle sur les enregistrements `HR_Movement` par processus de paie |
| Résolution destinataire | Utilise l'indicateur `C_BPartner.iEXT_EMailRecipient` |
| Stratégie mot de passe | Mot de passe dynamique par employé depuis les données BPartner |
| Suivi | Positionne l'indicateur `iEXT_EmailSent` après livraison réussie |

---

## Licence

GPLv2 — le partage est une vertu.

---

## Crédits

- Concept original : **[hieplq](https://github.com/hieplq)**
- Sponsorisé initialement par : **Ray Lee**
- Portage iDempiere 7.1 & personnalisation paie RH : **Corneille NGALEU**
- Wiki : https://wiki.idempiere.org/en/Extend_Jasper_Engine
