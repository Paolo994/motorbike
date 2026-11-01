-- Taratura delle parole chiave dopo il primo import reale (400 articoli):
-- "nuovo"/"nuova" erano troppo generiche ("Quiles di nuovo in pole" finiva tra i modelli);
-- abbigliamento e accessori finivano nel topic predefinito della fonte (es. MotoGP per GPOne).
UPDATE news_topics SET keywords =
 'prova,provata,test ride,first ride,review,recensione,novita,anteprima,presentata,presentato,svelata,eicma,listino,prezzo,usata,usato,model year,unveiled,launch,debut,naked,enduro,adventure,crossover,scooter,maxi scooter,hypernaked,sportiva,touring,nuovo modello,nuova moto,gamma 2026,gamma 2027'
WHERE slug = 'modelli';

UPDATE news_topics SET keywords =
 'mercato,vendite,immatricolazioni,sales,industry,richiamo,recall,sicurezza,codice della strada,casco,caschi,airbag,assicurazione,bollo,autostrada,pedaggio,incentivi,elettrica,electric,giacca,giacche,abbigliamento,guanti,stivali,tuta,accessori,equipaggiamento,interfono,all''asta,asta,auction'
WHERE slug = 'dal-mondo';
