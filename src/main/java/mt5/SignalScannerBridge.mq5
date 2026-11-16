//+------------------------------------------------------------------+
//| SignalScannerBridge.mq5                                              |
//| Pont fichier local entre le bot Java signalScaner et                |
//| MetaTrader 5. Attache-le a n'importe quel graphique, active        |
//| AutoTrading. Utilise le dossier "Common\Files" (partage entre      |
//| tous les terminaux MT5 de la machine) pour echanger avec le bot.   |
//+------------------------------------------------------------------+
#property strict
#include <Trade\Trade.mqh>

// TEST UNIQUEMENT (jamais decommente dans la version livree) : simule l'echec de la pose du stop pour
// verifier que la position est bien fermee. Voir le guide de test C1.
//#define ICT_TEST_STOP_FAILURE

CTrade trade;

input long   MagicNumber   = 990016;
input string CommandsFile  = "signalScanner_commands.txt";
input string ResultsFile   = "signalScanner_results.txt";
input string PositionsFile = "signalScanner_positions.json";
input string RiskMetaFile  = "signalScanner_risk_meta.txt";
input string HistoryFile   = "signalScanner_history.json";
input int    PollSeconds   = 3;
// Plafond de securite absolu, independant de la config du bot Java — dernier
// rempart si un bug venait un jour a faire executer la meme commande en boucle.
input int    MaxOpenPositions = 5;
// 30/10/2026 (decision de Mohammed) : des que TP2 est pris, le stop monte a +StopAfterTp2R x le risque initial (0 = comportement precedent : stop au point d'entree).
input double StopAfterTp2R = 1.5;
// Fichier facultatif ecrit par le dashboard (onglet Parametres) pour changer
// MaxOpenPositions/PollSeconds a chaud, sans recompiler ni redemarrer MT5 —
// absent = comportement inchange (valeurs input ci-dessus). Meme dossier
// Common\Files que le reste du pont.
input string RuntimeParamsFile = "signalScanner_runtime_params.txt";

// 07/10/2026 : l'EA ecrit LUI-MEME les bougies M5/M15 du symbole de SON graphique (prix du courtier) pour le bot Java — plus d'EA de prix separe.
// Fichiers (Common\Files) : feed_<FeedKey>_M5.csv, feed_<FeedKey>_M15.csv, feed_<FeedKey>_heartbeat.json. Attache l'EA au graphique du symbole echange.
input string FeedKey     = "ea_signalScanner";
input int    FeedBarsM5  = 900;     // ~3 jours de M5
input int    FeedBarsM15 = 1500;    // ~15 jours de M15

// Valeurs reellement utilisees a l'execution — demarrent avec les defauts
// input ci-dessus, puis mises a jour a chaud (sans recompiler ni redemarrer
// MT5) via RuntimeParamsFile si ce fichier existe et contient des valeurs
// valides. LoadRuntimeParams() est appelee a chaque OnTimer.
int g_MaxOpenPositions = 0;
int g_PollSeconds = 0;
int g_ActiveTimerSeconds = 0;

// --- Stop de secours (C1, 10/10/2026) : jamais de position sans stop-loss ---
input bool CloseNakedPositions = true;  // ferme (ou repose le stop de) toute position de ce robot restee sans SL
input int  StopAttachRetries   = 4;     // essais de pose du stop apres une ouverture sans stop (brokers 10016)
input int  StopFailCooldownMin = 15;    // apres un echec de stop : aucun nouvel ordre sur ce symbole pendant N minutes
string   g_cooldownSymbol[];
datetime g_cooldownUntil[];

void LoadRuntimeParams()
{
   int maxPos = MaxOpenPositions;
   int poll = PollSeconds;
   if(FileIsExist(RuntimeParamsFile, FILE_COMMON))
   {
      int h = FileOpen(RuntimeParamsFile, FILE_READ|FILE_TXT|FILE_COMMON|FILE_ANSI);
      if(h != INVALID_HANDLE)
      {
         while(!FileIsEnding(h))
         {
            string line = FileReadString(h);
            if(StringLen(line) == 0)
               continue;
            string parts[];
            int n = StringSplit(line, '|', parts);
            if(n < 2)
               continue;
            if(parts[0] == "MaxOpenPositions")
               maxPos = (int)StringToInteger(parts[1]);
            else if(parts[0] == "PollSeconds")
               poll = (int)StringToInteger(parts[1]);
         }
         FileClose(h);
      }
   }
   if(maxPos < 1) maxPos = MaxOpenPositions;
   if(poll < 1) poll = PollSeconds;
   g_MaxOpenPositions = maxPos;
   g_PollSeconds = poll;
   if(g_PollSeconds != g_ActiveTimerSeconds)
   {
      EventKillTimer();
      EventSetTimer(g_PollSeconds);
      g_ActiveTimerSeconds = g_PollSeconds;
   }
}

//+------------------------------------------------------------------+
//| Nombre de positions ouvertes actuellement pour ce Magic Number     |
//+------------------------------------------------------------------+
int CountMagicPositions()
{
   int total = 0;
   for(int i = 0; i < PositionsTotal(); i++)
   {
      ulong ticket = PositionGetTicket(i);
      if(ticket != 0 && PositionGetInteger(POSITION_MAGIC) == MagicNumber)
         total++;
   }
   return total;
}

//+------------------------------------------------------------------+
//| Flux de prix du graphique -> bot Java (07/10/2026)                |
//+------------------------------------------------------------------+
datetime g_feedLastWrite[2];
double   g_feedLastClose[2];
datetime g_feedLastBar[2];

// Decalage serveur - UTC (secondes), robuste week-end/marche ferme.
long FeedServerOffset()
{
   long off = (long)TimeTradeServer() - (long)TimeGMT();
   return (long)MathRound((double)off / 900.0) * 900;
}

// 25/10/2026 : conversion heure serveur -> UTC BOUGIE PAR BOUGIE (changement d'heure). Regle du courtier RaiseGlobal, verifiee sur l'historique 2026
// (l'ouverture hebdomadaire tombe a 00:00 heure serveur toute l'annee, y compris les semaines ou l'Europe et les Etats-Unis ne changent pas d'heure le meme jour) :
// heure serveur = heure de New York + 7 h, avec l'heure d'ete americaine (2e dimanche de mars 02:00 -> 1er dimanche de novembre 02:00).
// Decalage serveur - UTC : +3 h pendant l'heure d'ete americaine, +2 h en hiver.
datetime NthSundayOfMonth(int year, int month, int n)
{
   MqlDateTime d;
   d.year = year; d.mon = month; d.day = 1; d.hour = 0; d.min = 0; d.sec = 0;
   datetime first = StructToTime(d);
   MqlDateTime f;
   TimeToStruct(first, f);
   int add = (7 - f.day_of_week) % 7;      // 0 si le 1er du mois est deja un dimanche (dimanche = 0 en MQL5)
   return first + (datetime)((add + 7 * (n - 1)) * 86400);
}

bool UsDstAtNyWall(datetime nyWall)
{
   MqlDateTime d;
   TimeToStruct(nyWall, d);
   datetime start = NthSundayOfMonth(d.year, 3, 2) + 2 * 3600;
   datetime stop  = NthSundayOfMonth(d.year, 11, 1) + 2 * 3600;
   return nyWall >= start && nyWall < stop;
}

long ServerToUtcOffsetRule(datetime serverTime)
{
   return UsDstAtNyWall(serverTime - 7 * 3600) ? 3 * 3600 : 2 * 3600;
}

bool g_feedRuleOk = true;     // false = la regle ne colle pas au decalage mesure sur le terminal : repli sur l'ancien comportement (decalage mesure unique)

// Auto-test (une fois au demarrage) : decalages attendus, comparables a ceux du script de controle.
void DstSelfTest()
{
   datetime t[] = {D'2026.03.07 12:00', D'2026.03.09 12:00', D'2026.07.01 12:00', D'2026.10.25 12:00', D'2026.11.01 12:00', D'2026.11.02 12:00', D'2027.03.13 12:00', D'2027.03.15 12:00'};
   for(int i = 0; i < ArraySize(t); i++)
      Print("DST-TEST ", TimeToString(t[i], TIME_DATE|TIME_MINUTES), " serveur -> decalage ", (int)(ServerToUtcOffsetRule(t[i]) / 3600), " h");
}

void WriteFeedTF(int idx, ENUM_TIMEFRAMES tf, string tfName, int want, long offset)
{
   MqlRates r[];
   ArraySetAsSeries(r, false);
   int got = CopyRates(_Symbol, tf, 0, want, r);
   if(got <= 0) return;
   double lastClose = r[got - 1].close;
   datetime lastBar = r[got - 1].time;
   datetime now = TimeLocal();
   if(lastClose == g_feedLastClose[idx] && lastBar == g_feedLastBar[idx] && (now - g_feedLastWrite[idx]) < 30) return;
   string tmp = "feed_" + FeedKey + "_" + tfName + ".tmp";
   string dst = "feed_" + FeedKey + "_" + tfName + ".csv";
   int h = FileOpen(tmp, FILE_WRITE | FILE_TXT | FILE_COMMON | FILE_ANSI);
   if(h == INVALID_HANDLE) return;
   FileWriteString(h, "time_utc,open,high,low,close,tick_volume\n");
   int digits = (int)SymbolInfoInteger(_Symbol, SYMBOL_DIGITS);
   for(int i = 0; i < got; i++)
   {
      long tu = (long)r[i].time - (g_feedRuleOk ? ServerToUtcOffsetRule(r[i].time) : offset);
      FileWriteString(h, StringFormat("%I64d,%s,%s,%s,%s,%I64d\n", tu,
                      DoubleToString(r[i].open, digits), DoubleToString(r[i].high, digits),
                      DoubleToString(r[i].low, digits),  DoubleToString(r[i].close, digits),
                      (long)r[i].tick_volume));
   }
   FileClose(h);
   FileMove(tmp, FILE_COMMON, dst, FILE_REWRITE | FILE_COMMON);   // remplacement atomique : le bot ne lit jamais un fichier a moitie ecrit
   g_feedLastClose[idx] = lastClose; g_feedLastBar[idx] = lastBar; g_feedLastWrite[idx] = now;
}

void WriteOwnFeed()
{
   long offset = FeedServerOffset();
   g_feedRuleOk = (ServerToUtcOffsetRule(TimeTradeServer()) == offset);   // la regle doit retrouver le decalage mesure maintenant
   datetime now = TimeLocal();
   if(g_feedLastWrite[0] == 0 || (now - g_feedLastWrite[0]) >= 4)  WriteFeedTF(0, PERIOD_M5,  "M5",  FeedBarsM5,  offset);
   if(g_feedLastWrite[1] == 0 || (now - g_feedLastWrite[1]) >= 10) WriteFeedTF(1, PERIOD_M15, "M15", FeedBarsM15, offset);
   int h = FileOpen("feed_" + FeedKey + "_heartbeat.tmp", FILE_WRITE | FILE_TXT | FILE_COMMON | FILE_ANSI);
   if(h != INVALID_HANDLE)
   {
      FileWriteString(h, StringFormat("{\"utc\": %I64d, \"server_offset\": %I64d, \"feed_mode\": \"%s\", \"connected\": %s, \"symbol\": \"%s\", \"version\": \"2.2-tp2stop\"}",
                      (long)TimeGMT(), offset, (g_feedRuleOk ? "rule" : "measured"), TerminalInfoInteger(TERMINAL_CONNECTED) ? "true" : "false", _Symbol));
      FileClose(h);
      FileMove("feed_" + FeedKey + "_heartbeat.tmp", FILE_COMMON, "feed_" + FeedKey + "_heartbeat.json", FILE_REWRITE | FILE_COMMON);
   }
}

// 19/10/2026 (audit MQ-03) : suivi des essais de TP1/TP2 par position (en memoire) + journal d'alertes lisible (<prefixe>_alerts.txt).
long g_pcIds[];
int  g_pcTries1[];
int  g_pcTries2[];
#define PC_MAX_TRIES 30

int PcIndex(long id)
{
   for(int k = 0; k < ArraySize(g_pcIds); k++)
      if(g_pcIds[k] == id) return k;
   int n = ArraySize(g_pcIds);
   ArrayResize(g_pcIds, n + 1); ArrayResize(g_pcTries1, n + 1); ArrayResize(g_pcTries2, n + 1);
   g_pcIds[n] = id; g_pcTries1[n] = 0; g_pcTries2[n] = 0;
   return n;
}

void WritePcAlert(string msg)
{
   Print(msg);
   int h = FileOpen(ResultsFile + ".alerts.txt", FILE_READ|FILE_WRITE|FILE_TXT|FILE_COMMON|FILE_ANSI|FILE_SHARE_READ|FILE_SHARE_WRITE);
   if(h == INVALID_HANDLE) return;
   FileSeek(h, 0, SEEK_END);
   FileWriteString(h, TimeToString(TimeCurrent(), TIME_DATE|TIME_SECONDS) + "|" + msg + "\n");
   FileClose(h);
}

int OnInit()
{
   DstSelfTest();
   trade.SetMarginMode();   // 19/10/2026 : relit le mode du compte (hedging) : l'objet global CTrade le lit a sa creation, avant la connexion au compte au demarrage de MetaTrader -> clotures partielles refusees en silence.
   if(TerminalInfoInteger(TERMINAL_CONNECTED) && AccountInfoInteger(ACCOUNT_MARGIN_MODE) != ACCOUNT_MARGIN_MODE_RETAIL_HEDGING)
      WritePcAlert("ATTENTION : le compte n'est pas en mode hedging (mode=" + IntegerToString((int)AccountInfoInteger(ACCOUNT_MARGIN_MODE)) + ") : les clotures partielles TP1/TP2 sont impossibles, le trade ira au TP3 ou au break-even.");
   trade.SetExpertMagicNumber(MagicNumber);
   LoadRuntimeParams();
   SymbolSelect(_Symbol, true);
   WriteOwnFeed();
   WritePositions();
   Print("Sp500Bridge demarre. Magic=", MagicNumber, " poll=", g_PollSeconds, "s, max positions=", g_MaxOpenPositions);
   return(INIT_SUCCEEDED);
}

void OnDeinit(const int reason)
{
   EventKillTimer();
}

void OnTimer()
{
   LoadRuntimeParams();
   ProcessCommands();
   EnforceStops();
   ManagePartialCloses();
   WritePositions();
   WriteHistory();
   WriteOwnFeed();
}

//+------------------------------------------------------------------+
//| Lit les commandes en attente, les execute, vide le fichier         |
//+------------------------------------------------------------------+
void ProcessCommands()
{
   if(!FileIsExist(CommandsFile, FILE_COMMON))
      return;

   int h = FileOpen(CommandsFile, FILE_READ|FILE_TXT|FILE_COMMON|FILE_ANSI);
   if(h == INVALID_HANDLE)
      return;

   string lines[];
   int count = 0;
   while(!FileIsEnding(h))
   {
      string line = FileReadString(h);
      if(StringLen(line) > 0)
      {
         ArrayResize(lines, count + 1);
         lines[count] = line;
         count++;
      }
   }
   FileClose(h);

   if(count == 0)
      return;

   // On vide le fichier tout de suite pour ne pas re-executer ces lignes au prochain tick.
   // Si l'ouverture en ecriture echoue (conflit d'acces avec le bot Java qui ecrit au
   // meme moment), on NE DOIT PAS executer les commandes deja lues : on les perdrait de
   // vue sans certitude que le fichier a bien ete vide, et elles seraient relues (donc
   // re-executees) au prochain tick. On retente au prochain OnTimer plutot que de risquer
   // une double execution d'un ordre reel.
   int th = FileOpen(CommandsFile, FILE_WRITE|FILE_TXT|FILE_COMMON|FILE_ANSI);
   if(th == INVALID_HANDLE)
   {
      Print("ProcessCommands: echec du vidage du fichier de commandes, on reessaie au prochain cycle (aucun ordre execute ce tick).");
      return;
   }
   // Ouvrir en FILE_WRITE puis fermer SANS rien ecrire ne tronque pas
   // reellement le fichier sur cet environnement — constate en prod (une
   // commande deja executee/refusee restait indefiniment dans le fichier et
   // etait retentee en boucle). Ecrire explicitement une chaine vide force
   // la troncature reelle du fichier sur disque.
   FileWriteString(th, "");
   FileClose(th);

   for(int i = 0; i < count; i++)
      ExecuteCommand(lines[i]);
}

//+------------------------------------------------------------------+
//| Stop de secours (C1) : pause par symbole, pose du stop verifiee,   |
//| fermeture de securite et balayage des positions sans stop.         |
//+------------------------------------------------------------------+
bool SymbolInStopCooldown(string symbol)
{
   datetime now = TimeCurrent();
   for(int i = 0; i < ArraySize(g_cooldownSymbol); i++)
      if(g_cooldownSymbol[i] == symbol && g_cooldownUntil[i] > now)
         return true;
   return false;
}

void StartStopCooldown(string symbol)
{
   datetime until = TimeCurrent() + StopFailCooldownMin * 60;
   for(int i = 0; i < ArraySize(g_cooldownSymbol); i++)
   {
      if(g_cooldownSymbol[i] == symbol)
      {
         g_cooldownUntil[i] = until;
         return;
      }
   }
   int n = ArraySize(g_cooldownSymbol);
   ArrayResize(g_cooldownSymbol, n + 1);
   ArrayResize(g_cooldownUntil, n + 1);
   g_cooldownSymbol[n] = symbol;
   g_cooldownUntil[n] = until;
}

// Pose le SL/TP sur une position ouverte "nue" ; retente puis VERIFIE que le SL est bien present.
bool AttachStopWithRetry(ulong ticket, string symbol, double sl, double tp)
{
   int tries = StopAttachRetries;
   if(tries < 1) tries = 1;
   for(int attempt = 1; attempt <= tries; attempt++)
   {
      if(PositionSelectByTicket(ticket))
      {
#ifdef ICT_TEST_STOP_FAILURE
         Print("TEST C1 : echec SIMULE de la pose du stop (essai ", attempt, "/", tries, ") sur ", symbol);
#else
         if(trade.PositionModify(ticket, sl, tp))
         {
            if(PositionSelectByTicket(ticket) && PositionGetDouble(POSITION_SL) > 0)
            {
               Print("Stop pose sur ", symbol, " (essai ", attempt, "/", tries, ").");
               return true;
            }
         }
         Print("Echec de la pose du stop sur ", symbol, " (essai ", attempt, "/", tries, ") retcode=", trade.ResultRetcode());
#endif
      }
      else
         Print("Pose du stop : position introuvable (ticket ", ticket, ", essai ", attempt, "/", tries, ").");
      Sleep(300 * attempt);
   }
   return false;
}

// Ferme une position coute que coute (4 essais). Renvoie true si elle n'existe plus.
bool ClosePositionHard(ulong ticket)
{
   for(int i = 0; i < 4; i++)
   {
      if(!PositionSelectByTicket(ticket))
         return true;
      trade.SetMarginMode();
      if(trade.PositionClose(ticket))
         return true;
      Sleep(300);
   }
   return !PositionSelectByTicket(ticket);
}

// A chaque cycle : toute position de CE robot sans stop est remise sous stop (SL du risk-meta) ou fermee.
void EnforceStops()
{
   if(!CloseNakedPositions)
      return;
   long   ids[];
   double entries[], sls[], volumes[], contractSizes[], tp1s[], tp2s[];
   int    hit1s[], hit2s[];
   LoadRiskMeta(ids, entries, sls, volumes, contractSizes, tp1s, tp2s, hit1s, hit2s);

   for(int i = PositionsTotal() - 1; i >= 0; i--)
   {
      ulong ticket = PositionGetTicket(i);
      if(ticket == 0)
         continue;
      if(PositionGetInteger(POSITION_MAGIC) != MagicNumber)
         continue;
      if(PositionGetDouble(POSITION_SL) > 0)
         continue;

      string symbol = PositionGetString(POSITION_SYMBOL);
      long   positionId = PositionGetInteger(POSITION_IDENTIFIER);
      double currentTP = PositionGetDouble(POSITION_TP);
      Print("ALERTE : position ", positionId, " (", symbol, ") SANS STOP-LOSS detectee.");

      double metaSl = 0;
      for(int j = 0; j < ArraySize(ids); j++)
         if(ids[j] == positionId) { metaSl = sls[j]; break; }

      if(metaSl > 0 && trade.PositionModify(ticket, metaSl, currentTP)
         && PositionSelectByTicket(ticket) && PositionGetDouble(POSITION_SL) > 0)
      {
         Print("Stop repose sur la position ", positionId, " (", symbol, ") a ", metaSl, ".");
         continue;
      }
      bool closed = ClosePositionHard(ticket);
      StartStopCooldown(symbol);
      Print(closed ? "Position sans stop FERMEE par securite: " : "ECHEC de la fermeture de securite (reessai au prochain cycle): ", positionId, " ", symbol);
   }
}

//+------------------------------------------------------------------+
//| Format attendu: OPEN|SYMBOL|LONG_ou_SHORT|VOLUME|SL|TP1|TP2|TP3|COMMENT |
//| TP1/TP2 servent a la cloture partielle (1/3 a chaque niveau, voir  |
//| ManagePartialCloses) — le TP MT5 natif de la position reste TP3,   |
//| cible du dernier tiers.                                            |
//+------------------------------------------------------------------+
void ExecuteCommand(string line)
{
   string parts[];
   int n = StringSplit(line, '|', parts);
   if(n < 9)
   {
      LogResult(line, "ERROR", 0, "format invalide");
      return;
   }

   string action    = parts[0];
   string symbol    = parts[1];
   string direction = parts[2];
   double volume    = StringToDouble(parts[3]);
   double sl        = StringToDouble(parts[4]);
   double tp1       = StringToDouble(parts[5]);
   double tp2       = StringToDouble(parts[6]);
   double tp3       = StringToDouble(parts[7]);
   string comment   = parts[8];

   if(action != "OPEN")
   {
      LogResult(line, "ERROR", 0, "action inconnue: " + action);
      return;
   }

   if(!SymbolSelect(symbol, true))
   {
      LogResult(line, "ERROR", 0, "symbole introuvable: " + symbol);
      return;
   }

   if(SymbolInStopCooldown(symbol))
   {
      LogResult(line, "ERROR", 0, "pause de securite sur " + symbol + " apres un echec de pose du stop — ordre refuse");
      Print("ExecuteCommand: pause de securite apres echec de stop sur ", symbol, ", ordre refuse: ", line);
      return;
   }

   int openCount = CountMagicPositions();
   if(openCount >= g_MaxOpenPositions)
   {
      LogResult(line, "ERROR", 0, "plafond de securite atteint (" + IntegerToString(openCount) + "/" + IntegerToString(g_MaxOpenPositions) + " positions ouvertes) — ordre refuse");
      Print("ExecuteCommand: plafond de securite atteint, ordre refuse: ", line);
      return;
   }

   bool ok;
   ulong savedDeal = 0, savedOrder = 0;
   bool useSaved = false;
   if(direction == "LONG")
      ok = trade.Buy(volume, symbol, 0, sl, tp3, comment);
   else
      ok = trade.Sell(volume, symbol, 0, sl, tp3, comment);

   // Certains brokers en execution Marche rejettent un ordre d'ouverture qui
   // porte deja un SL/TP (retcode 10016, Invalid Stops), meme quand les prix
   // et le niveau de stops sont corrects — ils exigent d'ouvrir la position
   // NUE puis de lui ajouter le SL/TP par une modification separee juste
   // apres. On retente donc automatiquement de cette facon avant d'abandonner.
   if(!ok && trade.ResultRetcode() == 10016)
   {
      if(direction == "LONG")
         ok = trade.Buy(volume, symbol, 0, 0, 0, comment);
      else
         ok = trade.Sell(volume, symbol, 0, 0, 0, comment);

      if(ok)
      {
         // C1 (10/10/2026) : la position est ouverte NUE. Le stop est pose avec nouvelles tentatives et
         // verifie ; sinon la position est FERMEE par securite (jamais de position sans stop) et l'ordre
         // est renvoye en ERROR (avant : simple Print puis reponse "OK" avec une position sans stop).
         ulong ticket = trade.ResultOrder();
         savedOrder = ticket; savedDeal = trade.ResultDeal(); useSaved = true;   // MQ-01 : memorises AVANT la modification du stop
         if(!AttachStopWithRetry(ticket, symbol, sl, tp3))
         {
            int lastCode = (int)trade.ResultRetcode();
            bool closed = ClosePositionHard(ticket);
            StartStopCooldown(symbol);
            string why = "stop impossible a poser (retcode=" + IntegerToString(lastCode) + ") — " +
                         (closed ? "position fermee par securite" : "ALERTE : fermeture impossible, le balayage reessaiera");
            Print("ExecuteCommand: ", why, " sur ", symbol);
            LogResult(line, "ERROR", 0, why);
            return;
         }
      }
   }

   if(ok)
   {
      // 19/10/2026 (audit MQ-01) : apres le chemin « position nue + stop pose par modification », CTrade a vide son resultat
      // (deal/ordre = 0) : on utilise les tickets memorises juste apres l'ouverture.
      LogResult(line, "OK", (long)(useSaved ? savedOrder : trade.ResultOrder()), "");
      RecordOpenRisk(useSaved ? savedDeal : trade.ResultDeal(), symbol, sl, volume, tp1, tp2);
   }
   else
      LogResult(line, "ERROR", 0, "retcode=" + IntegerToString(trade.ResultRetcode()));
}

//+------------------------------------------------------------------+
//| Enregistre le risque initial (entree, stop, volume, taille du     |
//| contrat, TP1/TP2) d'une position tout juste ouverte, pour pouvoir  |
//| calculer son R reel a la cloture meme si le stop est deplace       |
//| ensuite, et pour piloter les clotures partielles (ManagePartial-   |
//| Closes). hit1/hit2 (0/0 au depart) suivent si TP1/TP2 ont deja ete |
//| traites.                                                            |
//+------------------------------------------------------------------+
void RecordOpenRisk(ulong dealTicket, string symbol, double sl, double volume, double tp1, double tp2)
{
   if(dealTicket == 0)
      return;
   if(!HistoryDealSelect(dealTicket))
      return;
   long positionId = HistoryDealGetInteger(dealTicket, DEAL_POSITION_ID);
   double entryPrice = HistoryDealGetDouble(dealTicket, DEAL_PRICE);
   double contractSize = SymbolInfoDouble(symbol, SYMBOL_TRADE_CONTRACT_SIZE);

   int h = FileOpen(RiskMetaFile, FILE_READ|FILE_WRITE|FILE_TXT|FILE_COMMON|FILE_ANSI);
   if(h == INVALID_HANDLE)
      return;
   FileSeek(h, 0, SEEK_END);
   FileWrite(h, IntegerToString(positionId) + "|" + DoubleToString(entryPrice, 5) + "|" +
              DoubleToString(sl, 5) + "|" + DoubleToString(volume, 2) + "|" + DoubleToString(contractSize, 2) + "|" +
              DoubleToString(tp1, 5) + "|" + DoubleToString(tp2, 5) + "|0|0");
   FileClose(h);
}

void LogResult(string original, string status, long ticket, string message)
{
   int h = FileOpen(ResultsFile, FILE_READ|FILE_WRITE|FILE_TXT|FILE_COMMON|FILE_ANSI);
   if(h == INVALID_HANDLE)
      return;
   FileSeek(h, 0, SEEK_END);
   FileWrite(h, TimeToString(TimeCurrent(), TIME_DATE|TIME_SECONDS) + "|" + original + "|" +
              status + "|" + IntegerToString((int)ticket) + "|" + message);
   FileClose(h);
}

//+------------------------------------------------------------------+
//| Ecrit le solde + les positions ouvertes par ce bot (meme magic)    |
//+------------------------------------------------------------------+
void WritePositions()
{
   double balance = AccountInfoDouble(ACCOUNT_BALANCE);
   double equity  = AccountInfoDouble(ACCOUNT_EQUITY);

   string json = "{\n";
   json += "  \"balance\": " + DoubleToString(balance, 2) + ",\n";
   json += "  \"equity\": " + DoubleToString(equity, 2) + ",\n";
   json += "  \"debug_connected\": " + (TerminalInfoInteger(TERMINAL_CONNECTED) ? "true" : "false") + ",\n";
   json += "  \"debug_trade_allowed\": " + (TerminalInfoInteger(TERMINAL_TRADE_ALLOWED) ? "true" : "false") + ",\n";
   json += "  \"debug_login\": " + IntegerToString((long)AccountInfoInteger(ACCOUNT_LOGIN)) + ",\n";
   json += "  \"debug_server\": \"" + AccountInfoString(ACCOUNT_SERVER) + "\",\n";
   json += "  \"debug_trade_mode\": " + IntegerToString(AccountInfoInteger(ACCOUNT_TRADE_MODE)) + ",\n";
   json += "  \"positions\": [\n";

   int total = PositionsTotal();
   bool first = true;
   for(int i = 0; i < total; i++)
   {
      ulong ticket = PositionGetTicket(i);
      if(ticket == 0)
         continue;
      if(PositionGetInteger(POSITION_MAGIC) != MagicNumber)
         continue;

      if(!first)
         json += ",\n";
      first = false;

      string sym      = PositionGetString(POSITION_SYMBOL);
      long   type     = PositionGetInteger(POSITION_TYPE);
      double vol       = PositionGetDouble(POSITION_VOLUME);
      double openPrice = PositionGetDouble(POSITION_PRICE_OPEN);
      double curPrice  = PositionGetDouble(POSITION_PRICE_CURRENT);
      double sl        = PositionGetDouble(POSITION_SL);
      double tp        = PositionGetDouble(POSITION_TP);
      double profit    = PositionGetDouble(POSITION_PROFIT);

      json += "    {";
      json += "\"ticket\": " + IntegerToString((int)ticket) + ", ";
      json += "\"symbol\": \"" + sym + "\", ";
      json += "\"direction\": \"" + (type == POSITION_TYPE_BUY ? "LONG" : "SHORT") + "\", ";
      json += "\"volume\": " + DoubleToString(vol, 2) + ", ";
      json += "\"entry\": " + DoubleToString(openPrice, 2) + ", ";
      json += "\"current\": " + DoubleToString(curPrice, 2) + ", ";
      json += "\"sl\": " + DoubleToString(sl, 2) + ", ";
      json += "\"tp\": " + DoubleToString(tp, 2) + ", ";
      json += "\"pl\": " + DoubleToString(profit, 2);
      json += "}";
   }

   json += "\n  ]\n}";

   int h = FileOpen(PositionsFile, FILE_WRITE|FILE_TXT|FILE_COMMON|FILE_ANSI);
   if(h == INVALID_HANDLE)
      return;
   FileWriteString(h, json);
   FileClose(h);
}

//+------------------------------------------------------------------+
//| Charge le risque initial de chaque position (enregistre par        |
//| RecordOpenRisk a l'ouverture) dans des tableaux paralleles.        |
//| Format legacy (5 champs, positions ouvertes avant l'ajout des      |
//| clotures partielles) : tp1/tp2 mis a 0 et hit1/hit2 a 1 — pas de   |
//| gestion partielle dessus, elles gardent leur comportement d'avant  |
//| (TP unique, aucune cloture partielle).                             |
//+------------------------------------------------------------------+
void LoadRiskMeta(long &ids[], double &entries[], double &sls[], double &volumes[], double &contractSizes[],
                   double &tp1s[], double &tp2s[], int &hit1s[], int &hit2s[])
{
   if(!FileIsExist(RiskMetaFile, FILE_COMMON))
      return;
   int h = FileOpen(RiskMetaFile, FILE_READ|FILE_TXT|FILE_COMMON|FILE_ANSI);
   if(h == INVALID_HANDLE)
      return;
   int count = 0;
   while(!FileIsEnding(h))
   {
      string line = FileReadString(h);
      if(StringLen(line) == 0)
         continue;
      string parts[];
      int n = StringSplit(line, '|', parts);
      if(n < 5)
         continue;
      ArrayResize(ids, count + 1);
      ArrayResize(entries, count + 1);
      ArrayResize(sls, count + 1);
      ArrayResize(volumes, count + 1);
      ArrayResize(contractSizes, count + 1);
      ArrayResize(tp1s, count + 1);
      ArrayResize(tp2s, count + 1);
      ArrayResize(hit1s, count + 1);
      ArrayResize(hit2s, count + 1);
      ids[count]           = (long)StringToInteger(parts[0]);
      entries[count]       = StringToDouble(parts[1]);
      sls[count]           = StringToDouble(parts[2]);
      volumes[count]       = StringToDouble(parts[3]);
      contractSizes[count] = StringToDouble(parts[4]);
      if(n >= 9)
      {
         tp1s[count]  = StringToDouble(parts[5]);
         tp2s[count]  = StringToDouble(parts[6]);
         hit1s[count] = (int)StringToInteger(parts[7]);
         hit2s[count] = (int)StringToInteger(parts[8]);
      }
      else
      {
         tp1s[count]  = 0;
         tp2s[count]  = 0;
         hit1s[count] = 1;
         hit2s[count] = 1;
      }
      count++;
   }
   FileClose(h);
}

//+------------------------------------------------------------------+
//| Reecrit entierement le fichier de risque (utilise apres une        |
//| cloture partielle, pour persister hit1/hit2).                      |
//+------------------------------------------------------------------+
void SaveRiskMeta(long &ids[], double &entries[], double &sls[], double &volumes[], double &contractSizes[],
                   double &tp1s[], double &tp2s[], int &hit1s[], int &hit2s[])
{
   int h = FileOpen(RiskMetaFile, FILE_WRITE|FILE_TXT|FILE_COMMON|FILE_ANSI);
   if(h == INVALID_HANDLE)
      return;
   for(int i = 0; i < ArraySize(ids); i++)
   {
      FileWrite(h, IntegerToString(ids[i]) + "|" + DoubleToString(entries[i], 5) + "|" +
                 DoubleToString(sls[i], 5) + "|" + DoubleToString(volumes[i], 2) + "|" +
                 DoubleToString(contractSizes[i], 2) + "|" + DoubleToString(tp1s[i], 5) + "|" +
                 DoubleToString(tp2s[i], 5) + "|" + IntegerToString(hit1s[i]) + "|" + IntegerToString(hit2s[i]));
   }
   FileClose(h);
}

//+------------------------------------------------------------------+
//| Cloture partielle a TP1/TP2 (1/3 du volume ORIGINAL a chaque       |
//| niveau) — reproduit exactement la logique deja simulee cote        |
//| moteur de journalisation (fraction 1/3) pour que les resultats      |
//| reels correspondent a ce qui est backteste/affiche. Stop remonte   |
//| au break-even des TP1 touche, jamais redeplace ensuite. Le dernier |
//| tiers reste sur son TP natif (TP3, deja regle a l'ouverture).      |
//+------------------------------------------------------------------+
void ManagePartialCloses()
{
   long   ids[];
   double entries[], sls[], volumes[], contractSizes[], tp1s[], tp2s[];
   int    hit1s[], hit2s[];
   LoadRiskMeta(ids, entries, sls, volumes, contractSizes, tp1s, tp2s, hit1s, hit2s);
   if(ArraySize(ids) == 0)
      return;

   bool changed = false;

   for(int i = 0; i < PositionsTotal(); i++)
   {
      ulong ticket = PositionGetTicket(i);
      if(ticket == 0)
         continue;
      if(PositionGetInteger(POSITION_MAGIC) != MagicNumber)
         continue;

      long positionId = PositionGetInteger(POSITION_IDENTIFIER);
      int idx = -1;
      for(int j = 0; j < ArraySize(ids); j++)
      {
         if(ids[j] == positionId) { idx = j; break; }
      }
      if(idx < 0)
         continue;
      if(hit1s[idx] == 1 && hit2s[idx] == 1)
         continue; // deja entierement traitee (ou position pre-existante sans TP1/TP2).

      string symbol    = PositionGetString(POSITION_SYMBOL);
      long   posType   = PositionGetInteger(POSITION_TYPE);
      double curPrice  = (posType == POSITION_TYPE_BUY) ? SymbolInfoDouble(symbol, SYMBOL_BID) : SymbolInfoDouble(symbol, SYMBOL_ASK);
      double volStep   = SymbolInfoDouble(symbol, SYMBOL_VOLUME_STEP);
      double volMin    = SymbolInfoDouble(symbol, SYMBOL_VOLUME_MIN);
      double curVolume = PositionGetDouble(POSITION_VOLUME);
      double currentTP = PositionGetDouble(POSITION_TP);
      if(volStep <= 0) volStep = 0.01;
      if(volMin <= 0) volMin = 0.01;

      bool reachedTp1 = (posType == POSITION_TYPE_BUY) ? (curPrice >= tp1s[idx]) : (curPrice <= tp1s[idx]);
      bool reachedTp2 = (posType == POSITION_TYPE_BUY) ? (curPrice >= tp2s[idx]) : (curPrice <= tp2s[idx]);

      if(hit1s[idx] == 0 && reachedTp1)
      {
         double closeVol = MathFloor((volumes[idx] / 3.0) / volStep) * volStep;
         double remaining = NormalizeDouble(curVolume - closeVol, 2);
         int pc = PcIndex(positionId);
         // 19/10/2026 (audit MQ-03) : TP1 n'est marque « traite » QUE si la partielle ET la remontee du stop ont reussi ;
         // sinon on reessaie a chaque cycle (PC_MAX_TRIES essais au plus, pour ne pas boucler sur un compte non hedging).
         bool alreadyPartial = (curVolume < volumes[idx] - volStep * 0.5);   // deja fait (ex. redemarrage de l'expert)
         bool partialOk = true;
         if(closeVol >= volMin && remaining >= volMin && !alreadyPartial)
         {
            trade.SetMarginMode();
            partialOk = trade.PositionClosePartial(ticket, closeVol);
            if(partialOk)
               Print("Cloture partielle TP1 sur position ", positionId, ": ", closeVol, " lots — stop remonte au break-even.");
            else
               Print("Echec cloture partielle TP1 sur position ", positionId, " (retcode=", trade.ResultRetcode(), ", mode de marge=", (int)AccountInfoInteger(ACCOUNT_MARGIN_MODE), ") essai ", g_pcTries1[pc] + 1, "/", PC_MAX_TRIES);
         }
         // Meme sans encaissement partiel (volume trop petit), le stop doit remonter au break-even : protection du capital.
         bool beOk = trade.PositionModify(ticket, entries[idx], currentTP);
         if(!beOk && trade.ResultRetcode() == 10025) beOk = true;   // 10025 = rien a modifier (stop deja au point d'entree)
         if(partialOk && beOk)
         {
            hit1s[idx] = 1;
            changed = true;
         }
         else
         {
            g_pcTries1[pc]++;
            if(g_pcTries1[pc] >= PC_MAX_TRIES)
            {
               WritePcAlert("ALERTE TP1 position " + IntegerToString(positionId) + " : " + (partialOk ? "" : "cloture partielle impossible ") + (beOk ? "" : "stop au point d'entree non pose ") + "apres " + IntegerToString(PC_MAX_TRIES) + " essais (compte non hedging ou refus du courtier) — etape abandonnee.");
               hit1s[idx] = 1;
               changed = true;
            }
         }
      }
      else if(hit1s[idx] == 1 && hit2s[idx] == 0 && reachedTp2)
      {
         double closeVol = MathFloor((volumes[idx] / 3.0) / volStep) * volStep;
         double remaining = NormalizeDouble(curVolume - closeVol, 2);
         int pc = PcIndex(positionId);
         bool alreadyPartial2 = (curVolume < volumes[idx] - closeVol - volStep * 0.5);   // deja fait (ex. redemarrage de l'expert)
         bool partialOk = true;
         if(closeVol >= volMin && remaining >= volMin && !alreadyPartial2)
         {
            trade.SetMarginMode();
            partialOk = trade.PositionClosePartial(ticket, closeVol);
            if(partialOk)
               Print("Cloture partielle TP2 sur position ", positionId, ": ", closeVol, " lots — dernier tiers vise TP3.");
            else
               Print("Echec cloture partielle TP2 sur position ", positionId, " (retcode=", trade.ResultRetcode(), ", mode de marge=", (int)AccountInfoInteger(ACCOUNT_MARGIN_MODE), ") essai ", g_pcTries2[pc] + 1, "/", PC_MAX_TRIES);
         }
         // 30/10/2026 : stop remonte a +StopAfterTp2R x le risque initial des que TP2 est pris (meme logique de reessai que le break-even de TP1).
         bool slOk = true;
         if(StopAfterTp2R > 0)
         {
            double riskDist = MathAbs(entries[idx] - sls[idx]);
            if(riskDist > 0)
            {
               int    digs  = (int)SymbolInfoInteger(symbol, SYMBOL_DIGITS);
               double newSl = NormalizeDouble(entries[idx] + ((posType == POSITION_TYPE_BUY) ? 1.0 : -1.0) * StopAfterTp2R * riskDist, digs);
               slOk = trade.PositionModify(ticket, newSl, currentTP);
               if(!slOk && trade.ResultRetcode() == 10025) slOk = true;   // 10025 = rien a modifier (stop deja a ce niveau)
               if(slOk)
                  Print("Stop remonte a +", StopAfterTp2R, "R (", newSl, ") sur position ", positionId, " apres TP2.");
               else
                  Print("Echec de la remontee du stop apres TP2 sur position ", positionId, " (retcode=", trade.ResultRetcode(), ") essai ", g_pcTries2[pc] + 1, "/", PC_MAX_TRIES);
            }
         }
         if(partialOk && slOk)
         {
            hit2s[idx] = 1;
            changed = true;
         }
         else
         {
            g_pcTries2[pc]++;
            if(g_pcTries2[pc] >= PC_MAX_TRIES)
            {
               WritePcAlert("ALERTE TP2 position " + IntegerToString(positionId) + " : " + (partialOk ? "" : "cloture partielle impossible ") + (slOk ? "" : "stop a +1,5R non pose ") + "apres " + IntegerToString(PC_MAX_TRIES) + " essais (compte non hedging ou refus du courtier) — etape abandonnee.");
               hit2s[idx] = 1;
               changed = true;
            }
         }
      }
   }

   if(changed)
      SaveRiskMeta(ids, entries, sls, volumes, contractSizes, tp1s, tp2s, hit1s, hit2s);
}

//+------------------------------------------------------------------+
//| Recalcule les stats de trading REEL (positions effectivement       |
//| executees par MT5, cloturees) a partir de l'historique du compte,  |
//| filtre par Magic Number — pas des alertes, de vraies executions.   |
//| Les deals de sortie (DEAL_ENTRY_OUT) sont agreges par position :    |
//| une position clotures en plusieurs fois (TP1 puis TP2 puis TP3)    |
//| genere plusieurs deals de sortie qui doivent compter comme UN SEUL |
//| trade, pas plusieurs — une position n'est comptee que lorsqu'elle  |
//| n'a plus AUCUN volume ouvert. Le R de chaque trade se base sur le  |
//| risque initial (entree/stop/volume au moment de l'ouverture), pas  |
//| le stop actuel qui a pu bouger (break-even apres TP1).             |
//+------------------------------------------------------------------+
void WriteHistory()
{
   long   riskIds[];
   double riskEntries[], riskSls[], riskVolumes[], riskContractSizes[], riskTp1s[], riskTp2s[];
   int    riskHit1s[], riskHit2s[];
   LoadRiskMeta(riskIds, riskEntries, riskSls, riskVolumes, riskContractSizes, riskTp1s, riskTp2s, riskHit1s, riskHit2s);

   HistorySelect(0, TimeCurrent());
   int dealsTotal = HistoryDealsTotal();

   long     posIds[];
   double   posProfits[];
   datetime posCloseTimes[];

   for(int i = 0; i < dealsTotal; i++)
   {
      ulong dealTicket = HistoryDealGetTicket(i);
      if(dealTicket == 0)
         continue;
      if(HistoryDealGetInteger(dealTicket, DEAL_MAGIC) != MagicNumber)
         continue;
      if(HistoryDealGetInteger(dealTicket, DEAL_ENTRY) != DEAL_ENTRY_OUT)
         continue;

      double profit = HistoryDealGetDouble(dealTicket, DEAL_PROFIT)
                     + HistoryDealGetDouble(dealTicket, DEAL_SWAP)
                     + HistoryDealGetDouble(dealTicket, DEAL_COMMISSION);
      long positionId = HistoryDealGetInteger(dealTicket, DEAL_POSITION_ID);
      datetime closeTime = (datetime)HistoryDealGetInteger(dealTicket, DEAL_TIME);

      int idx = -1;
      for(int j = 0; j < ArraySize(posIds); j++)
      {
         if(posIds[j] == positionId) { idx = j; break; }
      }
      if(idx < 0)
      {
         idx = ArraySize(posIds);
         ArrayResize(posIds, idx + 1);
         ArrayResize(posProfits, idx + 1);
         ArrayResize(posCloseTimes, idx + 1);
         posIds[idx] = positionId;
         posProfits[idx] = 0.0;
         posCloseTimes[idx] = 0;
      }
      posProfits[idx] += profit;
      if(closeTime > posCloseTimes[idx])
         posCloseTimes[idx] = closeTime;
   }

   int closedTrades = 0, wins = 0, losses = 0;
   double totalR = 0.0;
   datetime earliestClose = 0;

   for(int i = 0; i < ArraySize(posIds); i++)
   {
      bool stillOpen = false;
      for(int k = 0; k < PositionsTotal(); k++)
      {
         ulong t = PositionGetTicket(k);
         if(t != 0 && PositionGetInteger(POSITION_IDENTIFIER) == posIds[i])
         {
            stillOpen = true;
            break;
         }
      }
      if(stillOpen)
         continue;

      closedTrades++;
      if(posProfits[i] > 0) wins++; else losses++;
      if(earliestClose == 0 || posCloseTimes[i] < earliestClose)
         earliestClose = posCloseTimes[i];

      for(int j = 0; j < ArraySize(riskIds); j++)
      {
         if(riskIds[j] != posIds[i])
            continue;
         double riskDollars = MathAbs(riskEntries[j] - riskSls[j]) * riskVolumes[j] * riskContractSizes[j];
         if(riskDollars > 0)
            totalR += posProfits[i] / riskDollars;
         break;
      }
   }

   double winRate = closedTrades > 0 ? (double)wins / (double)closedTrades * 100.0 : 0.0;

   string json = "{\n";
   json += "  \"closed_trades\": " + IntegerToString(closedTrades) + ",\n";
   json += "  \"wins\": " + IntegerToString(wins) + ",\n";
   json += "  \"losses\": " + IntegerToString(losses) + ",\n";
   json += "  \"win_rate_pct\": " + DoubleToString(winRate, 1) + ",\n";
   json += "  \"total_r\": " + DoubleToString(totalR, 2) + ",\n";
   json += "  \"since\": \"" + (earliestClose > 0 ? TimeToString(earliestClose, TIME_DATE) : "") + "\"\n";
   json += "}";

   int h = FileOpen(HistoryFile, FILE_WRITE|FILE_TXT|FILE_COMMON|FILE_ANSI);
   if(h == INVALID_HANDLE)
      return;
   FileWriteString(h, json);
   FileClose(h);
}
//+------------------------------------------------------------------+
