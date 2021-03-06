package cz.cvut.dp.nss.search.utils;

import cz.cvut.dp.nss.search.entity.calendar.CalendarNode;
import cz.cvut.dp.nss.search.entity.calendarDate.CalendarDateNode;
import org.joda.time.LocalDate;
import org.joda.time.LocalDateTime;

import java.util.Set;

/**
 * DateTime utils.
 *
 * @author jakubchalupa
 * @since 12.02.17
 */
public class DateTimeUtils {

    public static final String DATE_TIME_PATTERN = "dd.MM.yyyy HH:mm";

    /**
     * pocet sekund za 24 hodin
     */
    public static final int SECONDS_IN_DAY = 86400;

    /**
     * 2,5 minuty minimalne nutne na prestup
     */
    public static final int MIN_TRANSFER_SECONDS = 150;

    /**
     * 5 minut penalizace za prestup pro pareto optimalitu
     */
    public static final int TRANSFER_PENALTY_MILLIS = 300;

    /**
     * @param calendarNode calendarNode proti kteremu overuji platnost localDateTime
     * @param localDateTime datum a cas, pro ktere kontroluji platnost v ramci calendarNode
     * @return true, pokud je localDateTime platny pro dany calendarNode, false jinak
     */
    public static boolean dateIsInCalendarValidity(final CalendarNode calendarNode, final LocalDateTime localDateTime) {
        //sekund since epoch od kdy plati celandar
        final long fromDateInSeconds = calendarNode.getFromDateInSeconds();
        //k datu do prictu jeden den, protoze posledni den uvazuji vcetne
        final long toDateInSeconds = calendarNode.getToDateInSeconds() + DateTimeUtils.SECONDS_IN_DAY;
        final long currentTimeInSeconds = localDateTime.toDate().getTime() / 1000;

        //pokud se vubec netrefim do intervalu platnosti
        if(fromDateInSeconds > currentTimeInSeconds || toDateInSeconds < currentTimeInSeconds) {
            return false;
        }

        //dale musim zkontrolovat, zda je interval platny pro dany den v tydnu a neni na nej uvalena vyjimka


        //prevedenim na localDate ztratim informace o hodinach/minutach ale zustane prave ten den, ve kterem jsem byl
        final LocalDate currentLocalDate = localDateTime.toLocalDate();
        final long currentDateInSeconds = currentLocalDate.toDate().getTime() / 1000;

        //null = pro current day neni vyjima, true = pro current day je include vyjimka, false = pro current day je exclude vyjimka
        Boolean includeCurrentDay = null;
        final Set<CalendarDateNode> calendarDateNodes = calendarNode.getCalendarDateNodes();
        if(calendarDateNodes != null && !calendarDateNodes.isEmpty()) {
            for(CalendarDateNode calendarDateNode : calendarDateNodes) {
                if(calendarDateNode.getDateInSeconds() == currentDateInSeconds) {
                    //pro aktualni den existuje vyjimka v jizdnim radu
                    includeCurrentDay = calendarDateNode.isInclude();
                    break;
                }
            }
        }

        final int currentDayOfWeek = localDateTime.getDayOfWeek();
        //pokud je aktualni den v platnosti a neni pro nej exclude vyjimka, nebo pokud je pro nej vyjimka include tak je to ok, jinak ne
        if((calendarNode.isValidInDayOfWeek(currentDayOfWeek) && !Boolean.FALSE.equals(includeCurrentDay)) || Boolean.TRUE.equals(includeCurrentDay)) {
            return true;
        }

        return false;
    }

    /**
     * @param departureDateTime cas od kdy zacinam vyhledavat (datum a cas ojezdu)
     * @param currentStopIsOverMidnightInTrip jestli je aktualne kontrolovany stop pres pulnoc v ramci tripu (tedy je po pulnoci ale trip vyjizdel pred pulnoci)
     * @param currentNodeTimeProperty cas aktualniho stopu
     * @param departureSecondsOfDay pocet vterin dne data odjezdu (tedy pocet vterin dne, ktere uplynou nez bude departure)
     * @return datum ke kontrole proti intervalu platnosti
     */
    public static LocalDateTime getDateTimeToValidateByDeparture(LocalDateTime departureDateTime, boolean currentStopIsOverMidnightInTrip,
                                                                 long currentNodeTimeProperty, int departureSecondsOfDay) {
        final LocalDateTime dateTimeToValidate;
        if(!currentStopIsOverMidnightInTrip) {
            //neprehoupl jsem se s tripem pres pulnoc (trip vyjel pred pulnoci a momentalne jsem porad pred pulnoci)
            if(currentNodeTimeProperty >= departureSecondsOfDay) {
                //neprehoupl jsem se pres pulnoc (pohybuji se v ramci dne, ve kterem jsem hledal odjezd)
                //zjistuji datum pro departureDateTime
                dateTimeToValidate = new LocalDateTime(departureDateTime);
            } else {
                //zjistuji platnost pro den nasledujici po departureDateTime (trip vyjel po pulnoci a ja jsem take po pulnoci)
                dateTimeToValidate = new LocalDateTime(departureDateTime).plusDays(1);
            }
        } else {
            //prehoupl jsem se s tripem pres pulnoc (trip vyjizdel pred pulnoci, ted uz je po pulnoci)
            if(currentNodeTimeProperty >= departureSecondsOfDay) {
                //neprehoupl jsem se pres pulnoc (pohybuji se v ramci dne, ve kterem jsem hledal odjezd)
                //zjistuji datum pro departureDateTime - 1 den protoze trip vyjel vcera
                dateTimeToValidate = new LocalDateTime(departureDateTime).minusDays(1);
            } else {
                //prehoupl jsem se pres pulnoc
                //zjistuji datum pro departureDateTime protoze trip vyjel pred pulnoci a ja taky
                dateTimeToValidate = new LocalDateTime(departureDateTime);
            }
        }

        return dateTimeToValidate;
    }

    /**
     * @param arrivalDateTime cas do kdy zacinam vyhledavat (datum a cas prijezdu)
     * @param currentStopIsOverMidnightInTrip jestli je aktualne kontrolovany stop pres pulnoc v ramci tripu (tedy je po pulnoci ale trip vyjizdel pred pulnoci)
     * @param currentNodeTimeProperty cas aktualniho stopu
     * @param arrivalSecondsOfDay pocet vterin dne data prijezdu (tedy pocet vterin dne, ktere uplynou nez bude arrival)
     * @return datum ke kontrole proti intervalu platnosti
     */
    public static LocalDateTime getDateTimeToValidateByArrival(LocalDateTime arrivalDateTime, boolean currentStopIsOverMidnightInTrip,
                                                                 long currentNodeTimeProperty, int arrivalSecondsOfDay) {
        final LocalDateTime dateTimeToValidate;
        if(!currentStopIsOverMidnightInTrip) {
            //neprehoupl jsem se s tripem pres pulnoc (trip vyjel pred pulnoci a momentalne jsem porad pred pulnoci)
            if(currentNodeTimeProperty <= arrivalSecondsOfDay) {
                //neprehoupl jsem se pres pulnoc (pohybuji se v ramci dne, ve kterem jsem hledal prijezd)
                //zjistuji datum pro arrivalDateTime
                dateTimeToValidate = new LocalDateTime(arrivalDateTime);
            } else {
                //zjistuji platnost pro den predchazejici arrivalDateTime (trip vyjel pred pulnoci a ja jsem take pred pulnoci)
                dateTimeToValidate = new LocalDateTime(arrivalDateTime).minusDays(1);
            }
        } else {
            //prehoupl jsem se s tripem pres pulnoc (trip vyjizdel pred pulnoci, ted uz je po pulnoci)
            if(currentNodeTimeProperty <= arrivalSecondsOfDay) {
                //neprehoupl jsem se pres pulnoc (pohybuji se v ramci dne, ve kterem jsem hledal prijezd)
                //zjistuji datum pro arrivalDateTime - 1 den protoze trip vyjel vcera
                dateTimeToValidate = new LocalDateTime(arrivalDateTime).minusDays(1);
            } else {
                //protoze currentStopIsOverMidnightInTrip se urcuje z casu departure tak muze dojit i k teto vetvi
                //v tom pripade se chovame stejne jako v if() podmince
                dateTimeToValidate = new LocalDateTime(arrivalDateTime).minusDays(1);
            }
        }

        return dateTimeToValidate;
    }

}
