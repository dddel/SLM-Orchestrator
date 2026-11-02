#!/usr/bin/env python3
"""Genera il dataset per confrontare modello decisionale e generativo.
Etichette corrette per costruzione: ogni campione nasce da una specifica (classe/ruolo + template + parametri).
Uso: python3 generate_decision_dataset.py --seed 20261030 --out OUTDIR
"""
import random, json, hashlib, argparse, os, re, datetime

# ---------- algoritmi di checksum ----------
MONTHS = "ABCDEHLMPRST"
ODD = [1,0,5,7,9,13,15,17,19,21,2,4,18,20,11,3,6,8,12,14,16,10,22,25,24,23]
def _idx(ch): return int(ch) if ch.isdigit() else ord(ch) - 65
def cf_check(c15):
    t = 0
    for i, ch in enumerate(c15):
        t += ODD[_idx(ch)] if i % 2 == 0 else _idx(ch)
    return chr(65 + t % 26)
def _codes(s, is_name):
    s = re.sub(r'[^A-Z]', '', s.upper())
    cons = [c for c in s if c not in "AEIOU"]; vow = [c for c in s if c in "AEIOU"]
    if is_name and len(cons) >= 4: cons = [cons[0], cons[2], cons[3]]
    return ("".join(cons) + "".join(vow) + "XXX")[:3]
COMUNI = {"Roma":"H501","Milano":"F205","Torino":"L219","Firenze":"D612","Napoli":"F839","Bologna":"A944","Palermo":"G273","Genova":"D969","Bari":"A662","Verona":"L781"}
def make_cf(surname, name, d, female, comune):
    c = _codes(surname, False) + _codes(name, True) + f"{d.year%100:02d}" + MONTHS[d.month-1] + f"{d.day+(40 if female else 0):02d}" + COMUNI[comune]
    return c + cf_check(c)
def make_piva(r):
    d = [r.randint(0,9) for _ in range(7)] + [int(x) for x in f"{r.randint(1,100):03d}"]
    s = 0
    for i, n in enumerate(d):
        if i % 2 == 1:
            n *= 2
            if n > 9: n -= 9
        s += n
    return "".join(map(str, d)) + str((10 - s % 10) % 10)
def make_iban(r):
    abi, cab, conto = (f"{r.randint(0,99999):05d}", f"{r.randint(0,99999):05d}", f"{r.randint(0,999999999999):012d}")
    t = 0
    for i, ch in enumerate(abi+cab+conto): t += ODD[_idx(ch)] if i % 2 == 0 else _idx(ch)
    bban = chr(65 + t % 26) + abi + cab + conto
    num = int("".join(str(int(c, 36)) for c in bban + "IT00"))
    return "IT" + f"{98 - num % 97:02d}" + bban

# ---------- utilità ----------
def eur(x):
    s = f"{x:,.2f}".replace(",", "X").replace(".", ",").replace("X", ".")
    return "€ " + s
def date(r, y0=2025, y1=2026):
    return datetime.date(r.randint(y0, y1), r.randint(1, 12), r.randint(1, 28))
def fd(d): return d.strftime("%d/%m/%Y")

COMP = ["Delta Servizi Srl","Alfa Forniture SpA","Beta Logistica Srl","Gamma Impianti Snc","Omega Consulting Srl","Studio Rinaldi & Associati","Verdi Costruzioni SpA","Nova Tecnologie Srl","Edil Sud Srl","Medi Sanitaria SpA","Arcobaleno Catering Srl","TecnoPrint Srl","Mediterranea Trasporti Srl","Lumina Energia Srl","Officine Bruni Snc","Fratelli Costa Srl"]
SVC = ["consulenza informatica","manutenzione impianti","fornitura materiale d'ufficio","servizi di pulizia","licenze software","trasporto merci","formazione del personale","assistenza tecnica","noleggio attrezzature","progettazione grafica"]
SURN = ["Rossi","Bianchi","Ferrari","Esposito","Romano","Colombo","Ricci","Marino","Greco","Bruno","Gallo","Conti","De Luca","Mancini","Costa","Giordano","Rizzo","Lombardi","Moretti","Barbieri"]
MALE = ["Marco","Luca","Andrea","Paolo","Giovanni","Stefano","Matteo","Davide","Roberto","Franco"]
FEM = ["Giulia","Laura","Chiara","Elena","Sara","Francesca","Anna","Marta","Silvia","Paola"]

def person(r):
    female = r.random() < 0.5
    name = r.choice(FEM if female else MALE); sn = r.choice(SURN)
    b = datetime.date(r.randint(1955, 2002), r.randint(1, 12), r.randint(1, 28))
    com = r.choice(list(COMUNI))
    return dict(name=name, surname=sn, birth=b, comune=com, female=female, cf=make_cf(sn, name, b, female, com))
def amounts(r):
    imp = round(r.uniform(150, 9000), 2); rate = r.choice([4, 10, 22])
    iva = round(imp*rate/100, 2); return imp, rate, iva, round(imp+iva, 2)

# ---------- OCR noise ----------
CONF = {"o":"0","l":"1","i":"l","s":"5","e":"c","a":"o","m":"rn","u":"v"}
def ocr_noise(r, text, p=0.10):
    def w(m):
        word = m.group(0)
        if r.random() > p*3: return word
        k = r.randrange(len(word)); ch = word[k]
        if r.random() < 0.3: return word[:k] + word[k+1:]
        return word[:k] + CONF.get(ch.lower(), ch) + word[k+1:]
    t = re.sub(r"\b[A-Za-zÀ-ÿ]{4,}\b", w, text)
    return t.replace("à","a").replace("è","e").replace("ù","u").replace("ò","o") if r.random() < 0.5 else t

# =====================================================================
# ROUTING: FATTURA / EMAIL / ANAGRAFICA / ALTRO
# Regola dichiarata: la classe segue la FORMA del documento.
#  FATTURA   = documento fiscale con titolo fattura/invoice, imponibile, IVA/VAT
#  EMAIL     = messaggio con intestazioni Da/A (From/To), anche se cita o inoltra altri documenti
#  ANAGRAFICA= scheda/record di dati identificativi di persona o ente, senza transazione né messaggio
#  ALTRO     = tutto il resto (preventivo e pro-forma NON sono fatture; DDT, verbali, contratti, circolari...)
# =====================================================================
def invoice_core(r, style):
    n, y = r.randint(1, 999), r.choice([2025, 2026]); c1, c2 = r.sample(COMP, 2)
    imp, rate, iva, tot = amounts(r); return dict(n=n, y=y, c1=c1, c2=c2, imp=imp, rate=rate, iva=iva, tot=tot,
        svc=r.choice(SVC), d1=fd(date(r)), d2=fd(date(r)), piva=make_piva(r), iban=make_iban(r))
def f_std(r):
    v = invoice_core(r, 0)
    return (f"FATTURA N. {v['n']}/{v['y']}\nData emissione: {v['d1']}\nScadenza pagamento: {v['d2']}\nCedente: {v['c1']} - P.IVA {v['piva']}\nCessionario: {v['c2']}\n"
            f"Descrizione: {v['svc']}\nImponibile: {eur(v['imp'])}\nIVA {v['rate']}%: {eur(v['iva'])}\nTotale documento: {eur(v['tot'])}\nPagamento tramite bonifico su IBAN {v['iban']}")
def f_tel(r):
    v = invoice_core(r, 0)
    return f"Ft. {v['n']}/{v['y']%100} {v['c1']}: imp. {eur(v['imp'])} + IVA {v['rate']}% = {eur(v['tot'])}. Saldo bonifico."
def f_letter(r):
    v = invoice_core(r, 0)
    return (f"FATTURA N. {v['n']}/{v['y']} del {v['d1']}\n{v['c1']} (P.IVA {v['piva']})\n\nGentile cliente,\nla ringraziamo per la fiducia accordataci. Di seguito il dettaglio dell'importo dovuto per {v['svc']}.\n"
            f"Imponibile {eur(v['imp'])} - IVA {v['rate']}% {eur(v['iva'])} - Totale {eur(v['tot'])}\nRestiamo a disposizione per qualsiasi chiarimento.\nCordiali saluti\n{v['c1']}")
def f_std2(r):
    v = invoice_core(r, 1)
    return (f"Fattura di vendita n. {v['n']}/{v['y']}\n{v['c1']} | P.IVA {v['piva']} | Emessa il {v['d1']}\nSpett.le {v['c2']}\nOggetto della prestazione: {v['svc']}\n"
            f"Base imponibile {eur(v['imp'])}\nAliquota IVA {v['rate']}% = {eur(v['iva'])}\nTotale da pagare {eur(v['tot'])} entro il {v['d2']}\nCoordinate: {v['iban']}")
def f_table(r):
    v = invoice_core(r, 2); rows = []; tot_imp = 0
    for i in range(r.randint(3, 6)):
        q = r.randint(1, 20); pr = round(r.uniform(10, 400), 2); tot_imp += q*pr
        rows.append(f"{i+1} | {r.choice(SVC)} | {q} | {eur(pr)} | {eur(q*pr)}")
    iva = round(tot_imp*0.22, 2)
    return (f"FATTURA ELETTRONICA {v['n']}/{v['y']}\nData documento {v['d1']} - Data consegna {v['d2']} - Scadenza {fd(date(r))}\nCedente {v['c1']} P.IVA {v['piva']}\n"
            "Riga | Descrizione | Qta | Prezzo | Importo\n" + "\n".join(rows) + f"\nTotale imponibile {eur(tot_imp)}\nTotale IVA 22% {eur(iva)}\nTotale fattura {eur(tot_imp+iva)}\nIBAN {v['iban']} (conto dedicato)")
def f_en(r):
    v = invoice_core(r, 3)
    return (f"INVOICE No. {v['n']}/{v['y']}\nIssued: {v['d1']}  Due: {v['d2']}\nSupplier: {v['c1']} VAT ID IT{v['piva']}\nServices: {v['svc']}\n"
            f"Taxable amount: {eur(v['imp'])}\nVAT {v['rate']}%: {eur(v['iva'])}\nTotal due: {eur(v['tot'])}\nPlease pay by bank transfer to {v['iban']}")
def f_tel2(r):
    v = invoice_core(r, 0)
    return f"FATT. {v['n']} - {v['svc']} - imponibile {eur(v['imp'])} IVA {v['rate']}% - tot. {eur(v['tot'])}"

HDR = lambda r: r.choice([("Da","A","Oggetto"),("From","To","Subject")])
def email_wrap(r, body, subj, en=False):
    d, a, o = ("From","To","Subject") if en else ("Da","A","Oggetto")
    x, y = r.sample(["amministrazione","ufficio.acquisti","mario.rossi","laura.verdi","info","segreteria","contabilita","hr"], 2)
    dom = r.choice(["azienda.it","studiocosta.it","fornitore.com","gruppoalfa.it"])
    return f"{d}: {x}@{dom}\n{a}: {y}@{r.choice(['azienda.it','cliente.com','mail.it'])}\n{o}: {subj}\n\n{body}"
def e_simple(r):
    return email_wrap(r, r.choice(["Buongiorno,\nconfermo l'incontro di giovedì alle 15 in sala riunioni. Porto io le slide.\nCordiali saluti","Ciao,\nti scrivo per sapere se hai ricevuto il materiale che ti avevo mandato ieri. Fammi sapere.\nA presto","Gentili colleghi,\nvi ricordo che l'ufficio resterà chiuso lunedì per la festività.\nBuon lavoro"]), r.choice(["Incontro di giovedì","Materiale","Chiusura ufficio"]))
def e_fwd_invoice(r):
    inv = r.choice([f_std, f_std2, f_en, f_table])(r)
    return email_wrap(r, "Ciao, ti inoltro il documento qui sotto, dimmi se è tutto a posto.\n\n---------- Messaggio inoltrato ----------\n" + inv, "Fwd: documento")
def e_cites_data(r):
    p = person(r); imp, rate, iva, tot = amounts(r)
    return email_wrap(r, f"Buongiorno,\nper completare la pratica servono copia del documento e codice fiscale ({p['cf']}) di {p['name']} {p['surname']}.\nPer il saldo di {eur(tot)} usate l'IBAN {make_iban(r)} indicando il numero fattura {r.randint(1,999)}/2026.\nGrazie", "Pratica e pagamento")
def e_short(r):
    return email_wrap(r, r.choice(["ok, a domani. L.","Ricevuto, grazie!","Va bene così, confermo.","Sì perfetto"]), "Re: " + r.choice(["ok","domani","conferma","grazie"]))
def e_en(r):
    return email_wrap(r, r.choice(["Hi,\nplease find attached the updated agenda for Monday. Let me know if anything is missing.\nBest regards","Dear all,\nthe invoice for last month has been sent to accounting, no action needed from your side.\nThanks"]), r.choice(["Agenda","Update"]), en=True)
def e_thread(r):
    inv = r.choice([f_tel, f_letter])(r)
    return email_wrap(r, "Perfetto, grazie. Procedo come d'accordo.\n\nIl giorno " + fd(date(r)) + " ha scritto:\n> " + inv.replace("\n", "\n> "), "Re: scambio")

def a_person(r):
    p = person(r)
    return (f"SCHEDA ANAGRAFICA DIPENDENTE\nCognome: {p['surname']}\nNome: {p['name']}\nNato/a a {p['comune']} il {fd(p['birth'])}\nCodice fiscale: {p['cf']}\nResidenza: via {r.choice(SURN)} {r.randint(1,200)}, {r.choice(list(COMUNI))}")
def a_supplier(r):
    return f"ANAGRAFICA FORNITORE\nRagione sociale: {r.choice(COMP)}\nP.IVA: {make_piva(r)}\nSede legale: via {r.choice(SURN)} {r.randint(1,200)}, {r.choice(list(COMUNI))}\nIBAN di riferimento: {make_iban(r)}\nReferente: {r.choice(MALE)} {r.choice(SURN)}"
def a_short(r):
    p = person(r); return f"CF: {p['cf']} - {p['name'].upper()} {p['surname'].upper()}, {p['comune'].upper()} {fd(p['birth'])}"
def a_person2(r):
    p = person(r)
    return (f"Anagrafica cliente n. {r.randint(1000,9999)}\nIntestatario: {p['surname']} {p['name']}\nData e luogo di nascita: {fd(p['birth'])}, {p['comune']}\nC.F. {p['cf']}\nTelefono: 3{r.randint(10,99)} {r.randint(1000000,9999999)}\nStato: attivo")
def a_contact(r):
    p = person(r)
    return (f"Contatto: {p['name']} {p['surname']}\nEmail: {p['name'].lower()}.{p['surname'].lower().replace(' ','')}@{r.choice(['azienda.it','mail.it'])}\nTel: 06 {r.randint(1000000,9999999)}\nCodice fiscale: {p['cf']}\nNote: referente amministrativo")
def a_supplier2(r):
    return (f"Dati ente\nDenominazione: {r.choice(COMP)}\nPartita IVA {make_piva(r)}\nIndirizzo: {r.choice(list(COMUNI))}, via {r.choice(SURN)} {r.randint(1,200)}\nIBAN {make_iban(r)}\nPEC: amministrazione@{r.choice(['pec.it','legalmail.it'])}")
def a_short2(r):
    p = person(r); return f"{p['surname'].upper()} {p['name'].upper()} nato a {p['comune']} {fd(p['birth'])} - {p['cf']}"

def o_verbale(r):
    return (f"VERBALE RIUNIONE DEL {fd(date(r))}\nPresenti: {r.choice(MALE)} {r.choice(SURN)}, {r.choice(FEM)} {r.choice(SURN)}\nOrdine del giorno: budget {r.choice(['marketing','formazione','IT'])}\nSi approva uno stanziamento di {eur(r.randint(2000,50000))} IVA inclusa. Prossima riunione tra due settimane.")
def o_preventivo(r):
    v = invoice_core(r, 0)
    return (f"PREVENTIVO N. {v['n']}/{v['y']}\nSpett.le {v['c2']}\nOfferta per {v['svc']}\nImponibile: {eur(v['imp'])}\nIVA {v['rate']}%: {eur(v['iva'])}\nTotale: {eur(v['tot'])}\nValidità 30 giorni. Il presente documento non è una fattura e non ha valore fiscale.")
def o_note(r):
    return f"[{fd(date(r))}] " + r.choice(["Promemoria: sostituire toner stampante piano 2 entro venerdì.","Ricordarsi di chiudere le finestre della sala riunioni.","Chiavi dell'archivio in portineria dopo le 18.","Aggiornare la lista dei turni di agosto."])
def o_circ(r):
    return (f"CIRCOLARE INTERNA N. {r.randint(1,60)}/2026\nOggetto: {r.choice(['nuovi orari','sicurezza sul lavoro','utilizzo parcheggio'])}\nSi comunica a tutto il personale che dal {fd(date(r))} è in vigore la nuova procedura. Il costo del servizio, pari a {eur(r.randint(100,5000))}, è a carico dell'azienda.\nLa Direzione")
def o_ddt(r):
    c1, c2 = r.sample(COMP, 2)
    return f"DOCUMENTO DI TRASPORTO (DDT) n. {r.randint(1,999)} del {fd(date(r))}\nMittente: {c1}\nDestinatario: {c2}\nMerci: {r.randint(1,40)} bancali di {r.choice(['componenti elettrici','materiale edile','imballaggi'])}\nCausale: vendita\nValore stimato ai fini assicurativi: {eur(r.randint(500,9000))}"
def o_contract(r):
    c1, c2 = r.sample(COMP, 2)
    return (f"CONTRATTO DI SERVIZIO tra {c1} e {c2}\nOggetto: {r.choice(SVC)}\nCorrispettivo annuo: {eur(r.randint(5000,90000))} + IVA, da versare in quattro rate su IBAN {make_iban(r)}.\nDurata: 12 mesi dalla data di firma.")
def o_report(r):
    return (f"RAPPORTO TECNICO {r.randint(1,99)}/2026\nIntervento del {fd(date(r))}: sostituzione di {r.choice(['pompa','compressore','centralina'])}. Tempo impiegato {r.randint(1,8)} ore, materiali per {eur(r.randint(50,900))}. Esito: positivo.")
def o_text(r):
    return f"({r.choice(['Nota','Appunto','Testo'])} {r.randint(1,999)}) " + r.choice(["Per la carbonara servono guanciale, uova, pecorino e pepe. Cuocere la pasta al dente e mantecare fuori dal fuoco.","La partita di sabato è stata rinviata per maltempo; nuova data da definire.","Il treno per Milano parte alle 8:15 dal binario 4, arrivo previsto alle 11:40.","Le piante vanno annaffiate due volte a settimana, evitando le ore più calde."])
def o_prev2(r):
    v = invoice_core(r, 1)
    return (f"PRO-FORMA n. {v['n']}\n{v['c1']} (P.IVA {v['piva']})\nImporto indicativo per {v['svc']}: imponibile {eur(v['imp'])}, IVA {v['rate']}% {eur(v['iva'])}, totale {eur(v['tot'])}.\nDocumento non fiscale: la fattura verrà emessa a conferma dell'ordine.")
def o_note2(r):
    return f"[{fd(date(r))}] " + r.choice(["Nota: riunione spostata a giovedì, sala B.","Da ricordare: ordinare cancelleria entro fine mese.","Appunto: il fornitore richiama lunedì per l'offerta."])

# (nome, funzione, classe, strato, split)
ROUTING_T = [
 ("f_std",f_std,"FATTURA","standard","dev"),("f_tel",f_tel,"FATTURA","breve","dev"),("f_letter",f_letter,"FATTURA","conflitto_lessicale","dev"),
 ("f_std2",f_std2,"FATTURA","standard","test"),("f_table",f_table,"FATTURA","lungo_distrattori","test"),("f_en",f_en,"FATTURA","standard","test"),
 ("f_tel2",f_tel2,"FATTURA","breve","test"),
 ("e_simple",e_simple,"EMAIL","standard","dev"),("e_fwd_invoice",e_fwd_invoice,"EMAIL","conflitto_lessicale","dev"),("e_short",e_short,"EMAIL","breve","dev"),
 ("e_cites_data",e_cites_data,"EMAIL","conflitto_lessicale","test"),("e_en",e_en,"EMAIL","standard","test"),("e_thread",e_thread,"EMAIL","lungo_distrattori","test"),
 ("a_person",a_person,"ANAGRAFICA","standard","dev"),("a_supplier",a_supplier,"ANAGRAFICA","conflitto_lessicale","dev"),("a_short",a_short,"ANAGRAFICA","breve","dev"),
 ("a_person2",a_person2,"ANAGRAFICA","standard","test"),("a_contact",a_contact,"ANAGRAFICA","conflitto_lessicale","test"),("a_supplier2",a_supplier2,"ANAGRAFICA","conflitto_lessicale","test"),("a_short2",a_short2,"ANAGRAFICA","breve","test"),
 ("o_verbale",o_verbale,"ALTRO","standard","dev"),("o_preventivo",o_preventivo,"ALTRO","conflitto_lessicale","dev"),("o_note",o_note,"ALTRO","breve","dev"),
 ("o_circ",o_circ,"ALTRO","standard","test"),("o_ddt",o_ddt,"ALTRO","conflitto_lessicale","test"),("o_contract",o_contract,"ALTRO","conflitto_lessicale","test"),
 ("o_report",o_report,"ALTRO","standard","test"),("o_text",o_text,"ALTRO","standard","test"),("o_prev2",o_prev2,"ALTRO","conflitto_lessicale","test"),("o_note2",o_note2,"ALTRO","breve","test"),
]

# =====================================================================
# INSTRADAMENTO EMAIL: direzione / risorse_umane / reparto_tecnico / amministrazione / legale
# Regola dichiarata: il ruolo è quello che possiede l'AZIONE RICHIESTA nel messaggio nuovo (righe non citate).
# Il contesto può evocare altri reparti (strato conflitto_contesto). Ogni testo contiene richieste di UN solo ruolo.
# =====================================================================
ASKS = {
 "direzione": ["autorizzare lo stanziamento straordinario","approvare il piano strategico del prossimo triennio","decidere sull'apertura della nuova sede","dare l'assenso del Consiglio di Amministrazione all'operazione","valutare la proposta di acquisizione","fissare gli obiettivi di crescita per l'anno","definire la politica degli investimenti","approvare il budget generale"],
 "risorse_umane": ["gestire l'assunzione del nuovo collaboratore","valutare la richiesta di ferie del dipendente","organizzare i colloqui per la posizione aperta","aggiornare il contratto di lavoro dello stagista","avviare l'onboarding del neoassunto","rivedere la mappatura dei turni del personale","gestire la richiesta di trasferimento interno","predisporre il piano di formazione dei dipendenti"],
 "reparto_tecnico": ["ripristinare il server","configurare la nuova postazione di lavoro","risolvere il guasto alla rete","aggiornare il firewall","installare l'aggiornamento di sistema","sostituire la stampante guasta","verificare il collegamento VPN","fare il backup dei dati del gestionale"],
 "amministrazione": ["registrare la fattura in contabilità","disporre il pagamento al fornitore","sollecitare l'incasso della fattura scaduta","riconciliare l'estratto conto bancario","emettere la nota di credito","verificare l'aliquota IVA applicata","aggiornare lo scadenzario dei pagamenti","chiudere la registrazione delle fatture del mese"],
 "legale": ["valutare l'azione legale","redigere la diffida di pagamento","verificare la clausola del contratto","rispondere al reclamo formale","predisporre la notifica al Garante Privacy","esaminare la richiesta di risarcimento","controllare la conformità normativa del testo","preparare la memoria difensiva"],
}
CTX = {
 "direzione": ["Il CdA ha discusso la strategia aziendale e gli investimenti del prossimo triennio.","La direzione generale segue da vicino il budget complessivo.","Il piano di crescita è stato presentato ai soci la scorsa settimana.","La governance dell'azienda richiede decisioni rapide sul progetto.","Il Direttore ha ricordato l'importanza dell'operazione per il gruppo.","I soci attendono indicazioni sulla strategia."],
 "risorse_umane": ["Il dipendente ha chiesto ferie e un colloquio sul proprio contratto.","La candidata ha inviato il curriculum e attende un riscontro.","Il personale del turno di notte lamenta carichi di lavoro elevati.","Lo stagista ha concluso il periodo di prova.","La busta paga di marzo riporta un errore sul TFR.","L'azienda cerca nuovo personale per il reparto."],
 "reparto_tecnico": ["Il server è lento da stamattina e la rete cade a intermittenza.","Sul gestionale compare un errore dopo l'ultimo aggiornamento.","Il malware rilevato ieri ha colpito un computer della sede.","La VPN non funziona da casa per tre utenti.","La stampante del primo piano segnala un guasto hardware.","Il software di contabilità si blocca all'avvio."],
 "amministrazione": ["La fattura del fornitore risulta scaduta e il bonifico non è partito.","Il cliente ha pagato in ritardo l'importo con IVA.","L'estratto conto riporta movimenti non ancora registrati.","La nota di credito va collegata alla fattura originale.","Lo scadenzario del mese presenta molte voci aperte.","Il fornitore chiede conferma dell'IBAN per i pagamenti."],
 "legale": ["Il cliente minaccia una causa e cita una clausola del contratto.","È arrivata una diffida da parte dello studio della controparte.","Il Garante Privacy ha chiesto chiarimenti sul trattamento dei dati.","Un dipendente contesta il licenziamento con il proprio avvocato.","Il fornitore contesta l'inadempimento contrattuale.","Il reclamo contiene un riferimento al GDPR."],
}
KW = {  # parole-spia usate SOLO dalla baseline lessicale, non dal generatore
 "direzione": ["cda","strategi","investiment","budget","soci","direttore","governance","direzione","triennio","crescita"],
 "risorse_umane": ["dipendent","ferie","assunzion","personale","colloqui","stagista","tfr","busta paga","curriculum","onboarding","contratto di lavoro"],
 "reparto_tecnico": ["server","rete","vpn","malware","firewall","stampante","backup","gestionale","software","computer","hardware"],
 "amministrazione": ["fattura","pagamento","iva","bonifico","incasso","estratto conto","scadenzario","nota di credito","contabilit","iban"],
 "legale": ["causa","diffida","clausola","garante","gdpr","reclamo","risarcimento","avvocato","contenzios","legale","contratto","azione"],
}
ROLES = list(ASKS)
FR = ["Vi chiediamo di {a}.","Serve che qualcuno si occupi di {a}.","Chiedo cortesemente di {a} entro venerdì.","Potreste {a}? Grazie.","Per favore, {a} appena possibile.","Resta da {a}."]
FR_TEL = ["{A}, grazie.","Urgente: {a}.","Per favore: {a}."]
OPEN = ["Buongiorno,","Gentili colleghi,","Ciao,","Egregi,",""]
CLOSE = ["Grazie e cordiali saluti.","Grazie.","Resto a disposizione.",""]
def split_list(lst, split): return lst[:3] if split == "dev" else lst[3:]
def split_ctx(lst, split): return lst[:2] if split == "dev" else lst[2:]
def build_email(r, role, strat, split):
    ask = r.choice(split_list(ASKS[role], split)); a_s = r.choice(FR).format(a=ask)
    ctxr = lambda x: r.choice(split_ctx(CTX[x], split))
    others = [x for x in ROLES if x != role]
    q = ""
    meta = dict(ask=ask, ctx_roles=[])
    if strat == "standard":
        body = f"{ctxr(role)} {a_s}"; meta["ctx_roles"] = [role]; subj = "Richiesta"
    elif strat == "conflitto_contesto":
        c1, c2 = r.sample(others, 2); body = f"{ctxr(c1)} {ctxr(c2)} {a_s}"; meta["ctx_roles"] = [c1, c2]; subj = r.choice(["Urgente","Aggiornamento","Segnalazione"])
    elif strat == "breve":
        body = r.choice(FR_TEL).format(a=ask, A=ask[0].upper()+ask[1:]); subj = r.choice(["Richiesta","Urgente","info"]); meta["ctx_roles"] = []
    elif strat == "thread":
        c = r.choice(others); old = f"{ctxr(c)} " + r.choice(FR).format(a=r.choice(split_list(ASKS[c], split)))
        body = f"{a_s}\n\nIl {fd(date(r))} ha scritto:\n> " + old.replace("\n", "\n> "); meta["ctx_roles"] = [c]; subj = "Re: pratica"; q = old
    elif strat == "lungo_distrattori":
        c1, c2, c3 = r.sample(others, 3); body = f"{ctxr(c1)} {ctxr(c2)} {ctxr(c3)} Detto questo, la mia richiesta è semplice: {a_s[0].lower()+a_s[1:]}"; meta["ctx_roles"] = [c1, c2, c3]; subj = "Situazione"
    op = r.choice(OPEN); cl = r.choice(CLOSE)
    text = (f"Oggetto: {subj}\n\n" + (op + "\n" if op else "") + body + ("\n" + cl if cl else ""))
    meta["quoted"] = q
    return text, meta

NEUTRAL = ["Lorem ipsum dolor sit amet, consectetur adipiscing elit, sed do eiusmod tempor incididunt ut labore.","a b c d e f g h i j k l m n o p","1 2 3 5 8 13 21 34 55 89 144","Oggi il cielo è nuvoloso con possibili schiarite nel pomeriggio.","Il gatto dorme sul divano mentre fuori piove.","Testo di prova. Testo di prova. Testo di prova.","xyz qwe asd zxc rty fgh vbn","Questo messaggio non contiene informazioni rilevanti.","...","Alba tramonto montagna fiume sasso","Il quick brown fox jumps over the lazy dog.","Sequenza: 10, 20, 30, 40, 50, 60.","Mi piace passeggiare al parco la domenica mattina.","Pagina 3 di 7","[contenuto non disponibile]","Rosso verde blu giallo viola arancione","Nulla da segnalare.","Il cielo è azzurro e l'aria è fresca.","Ok.","Parole sparse: tavolo, nuvola, bicicletta, lago, finestra.","Lorem ipsum.","Ut enim ad minim veniam, quis nostrud exercitation ullamco laboris.","Testo troncato che si interrompe a metà della fra","Frase generica senza alcun riferimento specifico a persone o cose.","A.B.C.D.E.F.G.","Il lunedì segue la domenica e precede il martedì.","Buona giornata a tutti.","Ciao ciao ciao.","Quattro tre due uno.","La luna gira intorno alla terra."]

def main():
    ap = argparse.ArgumentParser(); ap.add_argument("--seed", type=int, default=20261030); ap.add_argument("--out", default="."); ap.add_argument("--n-route", type=int, default=100); ap.add_argument("--n-email", type=int, default=100)
    a = ap.parse_args(); os.makedirs(a.out, exist_ok=True)
    rows = []
    # --- routing ---
    for split, n_per_class in (("dev", a.n_route//4), ("test", (a.n_route*3)//4)):
        for cls in ["FATTURA","EMAIL","ANAGRAFICA","ALTRO"]:
            tpls = [t for t in ROUTING_T if t[2] == cls and t[4] == split]
            r = random.Random(f"{a.seed}-route-{split}-{cls}")
            for i in range(n_per_class):
                name, fn, _, strat, _ = tpls[i % len(tpls)]
                clean = fn(r); noisy = strat in ("standard","breve") and r.random() < 0.25 and cls != "ANAGRAFICA" and len(clean) >= 80
                text = ocr_noise(r, clean) if noisy else clean
                rows.append(dict(id=f"R_{split}_{cls[:3]}_{i:03d}", task="router", split=split, label=cls, strata=strat + ("+ocr" if noisy else ""), template=name, text=text, clean_text=clean if noisy else None))
    # --- email ---
    stratmix = ["standard","conflitto_contesto","conflitto_contesto","breve","thread","lungo_distrattori"]
    for split, n_per_role in (("dev", a.n_email//5), ("test", (a.n_email*3)//5)):
        for role in ROLES:
            r = random.Random(f"{a.seed}-email-{split}-{role}")
            for i in range(n_per_role):
                strat = stratmix[i % len(stratmix)]
                clean, meta = build_email(r, role, strat, split)
                noisy = strat in ("standard","conflitto_contesto") and r.random() < 0.2
                text = ocr_noise(r, clean) if noisy else clean
                rows.append(dict(id=f"E_{split}_{role[:4]}_{i:03d}", task="email_role", split=split, label=role, strata=strat + ("+ocr" if noisy else ""), template=f"email:{strat}", text=text, clean_text=clean if noisy else None, ask=meta["ask"], ctx_roles=meta["ctx_roles"]))
    # --- neutri ---
    for i, t in enumerate(NEUTRAL):
        rows.append(dict(id=f"N_{i:03d}", task="neutral", split="test", label=None, strata="neutro", template="neutral", text=t, clean_text=None))
    random.Random(a.seed).shuffle(rows)
    for fn, filt in (("dataset_all.jsonl", lambda x: True), ("dataset_dev.jsonl", lambda x: x["split"]=="dev" and x["task"]!="neutral"), ("dataset_test.jsonl", lambda x: x["split"]=="test" and x["task"]!="neutral"), ("neutral.jsonl", lambda x: x["task"]=="neutral")):
        with open(os.path.join(a.out, fn), "w", encoding="utf-8") as f:
            for x in rows:
                if filt(x): f.write(json.dumps(x, ensure_ascii=False) + "\n")
    h = hashlib.sha256(open(os.path.join(a.out, "dataset_all.jsonl"), "rb").read()).hexdigest()
    json.dump(dict(seed=a.seed, sha256_dataset_all=h, n=len(rows), counts={k: sum(1 for x in rows if x["task"]==k) for k in ("router","email_role","neutral")}), open(os.path.join(a.out, "manifest.json"), "w"), indent=1)
    print("ok", len(rows), h)
if __name__ == "__main__": main()
