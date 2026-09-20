package co.edu.fet.agendad.agenda;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.validation.annotation.Validated;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.security.SecureRandom;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/v1")
@CrossOrigin(origins = "*")
@Validated
public class AgendaController {
  private final JdbcTemplate db; private final ObjectMapper json; private final KafkaTemplate<String,String> kafka;
  private static final SecureRandom RANDOM = new SecureRandom();
  public AgendaController(JdbcTemplate db, ObjectMapper json, KafkaTemplate<String,String> kafka) { this.db=db; this.json=json; this.kafka=kafka; }

  @GetMapping("/publico/{slug}/servicios") public List<Map<String,Object>> servicios(@PathVariable String slug) {
    UUID negocio=negocio(slug); return db.queryForList("select id,nombre,duracion_min as \"duracionMin\",precio from agenda.servicio where negocio_id=? and activo order by nombre", negocio);
  }
  @GetMapping("/publico/{slug}/disponibilidad") public Map<String,Object> disponibilidad(@PathVariable String slug,@RequestParam UUID servicioId,@RequestParam LocalDate fecha,@RequestParam(required=false) UUID profesionalId) {
    UUID negocio=negocio(slug); String zona=db.queryForObject("select zona_horaria from agenda.negocio where id=?",String.class,negocio);
    Integer minutos=db.queryForObject("select duracion_min from agenda.servicio where id=? and negocio_id=? and activo",Integer.class,servicioId,negocio);
    if(minutos==null) throw new NoEncontrado("No encontramos ese servicio.");
    String sql="select p.id,p.nombre from agenda.profesional p join agenda.servicio_profesional sp on sp.profesional_id=p.id where p.negocio_id=? and p.activo and sp.servicio_id=?"+(profesionalId==null?"":" and p.id=?");
    List<Object> args=new ArrayList<>(List.of(negocio,servicioId)); if(profesionalId!=null) args.add(profesionalId);
    List<Map<String,Object>> cupos=new ArrayList<>(); ZoneId z=ZoneId.of(zona);
    for(Map<String,Object> p:db.queryForList(sql,args.toArray())) {
      UUID pid=(UUID)p.get("id"); List<Map<String,Object>> horarios=db.queryForList("select hora_inicio,hora_fin from agenda.horario_atencion where profesional_id=? and dia_semana=?",pid,fecha.getDayOfWeek().getValue());
      for(Map<String,Object> h:horarios) { LocalTime ini=hora(h.get("hora_inicio")), fin=hora(h.get("hora_fin"));
        for(ZonedDateTime x=ZonedDateTime.of(fecha,ini,z); !x.plusMinutes(minutos).toLocalTime().isAfter(fin); x=x.plusMinutes(minutos)) { Instant a=x.toInstant(), b=x.plusMinutes(minutos).toInstant();
          Integer ocupadas=db.queryForObject("select count(*) from agenda.cita where profesional_id=? and estado<>'CANCELADA' and inicio<? and fin>?",Integer.class,pid,b,a);
          Integer bloqueos=db.queryForObject("select count(*) from agenda.bloqueo where profesional_id=? and inicio<? and fin>?",Integer.class,pid,b,a);
          if(ocupadas==0&&bloqueos==0) cupos.add(Map.of("inicio",a,"fin",b,"horaLocal",x.format(DateTimeFormatter.ofPattern("HH:mm")),"profesionalId",pid,"profesionalNombre",p.get("nombre")));
        }
      }
    } return Map.of("fecha",fecha,"zonaHoraria",zona,"cupos",cupos);
  }
  @PostMapping("/publico/{slug}/citas") @Transactional public ResponseEntity<?> reservar(@PathVariable String slug,@RequestHeader("Idempotency-Key") @NotBlank String key,@Valid @RequestBody Reserva r) {
    UUID negocio=negocio(slug); List<Map<String,Object>> prev=db.queryForList("select c.id,c.inicio,c.fin,c.estado,c.cliente_nombre,c.cliente_celular,s.nombre servicio,p.nombre profesional,t.token from agenda.cita c join agenda.servicio s on s.id=c.servicio_id join agenda.profesional p on p.id=c.profesional_id join agenda.token_gestion t on t.cita_id=c.id where c.negocio_id=? and c.idempotency_key=?",negocio,key);
    if(!prev.isEmpty()) return ResponseEntity.ok(respuesta(prev.getFirst(), slug));
    Map<String,Object> srv=one("select s.duracion_min,s.nombre,p.nombre profesional from agenda.servicio s join agenda.profesional p on p.id=? and p.negocio_id=s.negocio_id join agenda.servicio_profesional sp on sp.servicio_id=s.id and sp.profesional_id=p.id where s.id=? and s.negocio_id=? and s.activo",r.profesionalId(),r.servicioId(),negocio);
    Instant fin=r.inicio().plusSeconds(((Number)srv.get("duracion_min")).longValue()*60); UUID id=UUID.randomUUID(); String token=token();
    try { db.update("insert into agenda.cita(id,negocio_id,servicio_id,profesional_id,inicio,fin,cliente_nombre,cliente_celular,idempotency_key) values(?,?,?,?,?,?,?,?,?)",id,negocio,r.servicioId(),r.profesionalId(),r.inicio(),fin,r.clienteNombre(),r.clienteCelular(),key); }
    catch(DataIntegrityViolationException e) { return ResponseEntity.status(409).body(Map.of("codigo","CUPO_OCUPADO","mensaje","Ese cupo se acaba de ocupar. Estos son los más cercanos.","alternativas",List.of())); }
    db.update("insert into agenda.token_gestion(cita_id,token,expira_en) values(?,?,?)",id,token,Instant.now().plus(Duration.ofDays(30)));
    Map<String,Object> event=new LinkedHashMap<>(); event.put("eventoId",UUID.randomUUID()); event.put("version",1); event.put("ocurridoEn",Instant.now()); event.put("correlationId",key); event.put("negocioId",negocio); event.put("citaId",id); event.put("profesionalId",r.profesionalId()); event.put("profesionalNombre",srv.get("profesional")); event.put("servicioId",r.servicioId()); event.put("servicioNombre",srv.get("nombre")); event.put("inicio",r.inicio()); event.put("fin",fin); event.put("zonaHoraria",db.queryForObject("select zona_horaria from agenda.negocio where id=?",String.class,negocio)); event.put("cliente",Map.of("nombre",r.clienteNombre(),"celular",r.clienteCelular())); event.put("tokenGestion",token); outbox(negocio,"citas.reservadas",r.profesionalId().toString(),event);
    return ResponseEntity.status(201).body(Map.of("id",id,"inicio",r.inicio(),"fin",fin,"horaLocal",r.inicio().atZone(ZoneId.of("America/Bogota")).format(DateTimeFormatter.ofPattern("HH:mm")),"servicioNombre",srv.get("nombre"),"profesionalNombre",srv.get("profesional"),"estado","CONFIRMADA","enlaceGestion","/v1/gestion/"+token));
  }
  @GetMapping("/gestion/{token}") public Map<String,Object> gestion(@PathVariable String token) { return respuesta(one("select c.id,c.inicio,c.fin,c.estado,s.nombre servicio,p.nombre profesional,c.cliente_nombre,c.cliente_celular,t.token from agenda.token_gestion t join agenda.cita c on c.id=t.cita_id join agenda.servicio s on s.id=c.servicio_id join agenda.profesional p on p.id=c.profesional_id where t.token=? and t.revocado_en is null and t.expira_en>now()",token),""); }
  @DeleteMapping("/gestion/{token}") @Transactional public ResponseEntity<Void> cancelar(@PathVariable String token,@RequestParam boolean confirmar) {
    if(!confirmar) throw new IllegalArgumentException("Debes confirmar la cancelación.");
    Map<String,Object> c=one("select c.*,t.token from agenda.token_gestion t join agenda.cita c on c.id=t.cita_id where t.token=? and t.revocado_en is null",token);
    int actualizadas=db.update("update agenda.cita set estado='CANCELADA',actualizada_en=now() where id=? and estado='CONFIRMADA'",c.get("id"));
    if(actualizadas==0) return ResponseEntity.noContent().build();
    Map<String,Object> e=new LinkedHashMap<>(); e.put("eventoId",UUID.randomUUID()); e.put("version",1); e.put("ocurridoEn",Instant.now()); e.put("correlationId",token); e.put("negocioId",c.get("negocio_id")); e.put("citaId",c.get("id")); e.put("profesionalId",c.get("profesional_id")); e.put("servicioId",c.get("servicio_id")); e.put("inicio",instante(c.get("inicio"))); e.put("fin",instante(c.get("fin"))); e.put("canceladaPor","CLIENTE"); e.put("cliente",Map.of("nombre",c.get("cliente_nombre"),"celular",c.get("cliente_celular")));
    outbox((UUID)c.get("negocio_id"),"citas.canceladas",c.get("profesional_id").toString(),e); return ResponseEntity.noContent().build();
  }
  @GetMapping("/agenda/{profesionalId}") public List<Map<String,Object>> agenda(@RequestHeader("X-Negocio-Id") UUID negocio,@PathVariable UUID profesionalId,@RequestParam LocalDate fecha) { return db.queryForList("select c.id,c.inicio,c.fin,c.estado,c.cliente_nombre as \"clienteNombre\",s.nombre as \"servicioNombre\" from agenda.cita c join agenda.servicio s on s.id=c.servicio_id where c.negocio_id=? and c.profesional_id=? and c.inicio>=? and c.inicio<? order by c.inicio",negocio,profesionalId,fecha.atStartOfDay(ZoneOffset.UTC).toInstant(),fecha.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant()); }
  @PatchMapping("/citas/{id}/estado") @Transactional public ResponseEntity<Void> registrarEstado(@RequestHeader("X-Negocio-Id") UUID negocio,@PathVariable UUID id,@Valid @RequestBody EstadoRequest entrada) {
    Map<String,Object> c=one("select * from agenda.cita where id=? and negocio_id=?",id,negocio);
    if(!"CONFIRMADA".equals(c.get("estado"))) throw new IllegalArgumentException("Solo se puede registrar asistencia de una cita confirmada.");
    db.update("update agenda.cita set estado=?,actualizada_en=now() where id=?",entrada.estado(),id);
    Map<String,Object> e=new LinkedHashMap<>(); e.put("eventoId",UUID.randomUUID()); e.put("version",1); e.put("ocurridoEn",Instant.now()); e.put("correlationId",id.toString()); e.put("negocioId",negocio); e.put("citaId",id); e.put("profesionalId",c.get("profesional_id")); e.put("servicioId",c.get("servicio_id")); e.put("inicio",instante(c.get("inicio"))); e.put("fin",instante(c.get("fin"))); e.put("estadoAnterior","CONFIRMADA"); e.put("estadoNuevo",entrada.estado());
    outbox(negocio,"citas.estado",c.get("profesional_id").toString(),e); return ResponseEntity.noContent().build();
  }
  @GetMapping("/metricas") public Map<String,Object> metricas(@RequestHeader("X-Negocio-Id") UUID negocio,@RequestParam LocalDate desde,@RequestParam LocalDate hasta) {
    Map<String,Object> m=db.queryForMap("select coalesce(sum(reservadas),0) total,coalesce(sum(atendidas),0) atendidas,coalesce(sum(no_asistio),0) no_asistio,coalesce(sum(canceladas),0) canceladas from analitica.metrica_diaria where negocio_id=? and fecha between ? and ?",negocio,desde,hasta);
    long total=((Number)m.get("total")).longValue(), no=((Number)m.get("no_asistio")).longValue(); return Map.of("desde",desde,"hasta",hasta,"totalCitas",total,"atendidas",m.get("atendidas"),"noAsistio",no,"canceladas",m.get("canceladas"),"ocupacion",0,"tasaInasistencia",total==0?0:Math.round(no*10000.0/total)/100.0);
  }
  @GetMapping("/interno/panel") public Map<String,Object> panel(@RequestParam UUID negocioId,@RequestParam LocalDate fecha) { return Map.of("negocio",one("select id,nombre,slug_publico as \"slugPublico\",zona_horaria as \"zonaHoraria\" from agenda.negocio where id=?",negocioId),"servicios",db.queryForList("select id,nombre,duracion_min as \"duracionMin\",precio,activo from agenda.servicio where negocio_id=?",negocioId),"citas",db.queryForList("select c.id,c.inicio,c.fin,c.estado,c.cliente_nombre as \"clienteNombre\",c.cliente_celular as \"clienteCelular\",s.nombre as \"servicioNombre\",p.nombre as \"profesionalNombre\" from agenda.cita c join agenda.servicio s on s.id=c.servicio_id join agenda.profesional p on p.id=c.profesional_id where c.negocio_id=? and c.inicio>=? and c.inicio<?",negocioId,fecha.atStartOfDay(ZoneOffset.UTC).toInstant(),fecha.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant())); }
  @Scheduled(fixedDelay=2000) public void publicarPendientes() { for(Map<String,Object> o:db.queryForList("select id,tipo_evento,clave_particion,payload::text payload from agenda.outbox where enviado_en is null order by id limit 50")) try { kafka.send((String)o.get("tipo_evento"),(String)o.get("clave_particion"),(String)o.get("payload")).get(); db.update("update agenda.outbox set enviado_en=now(),intentos=intentos+1 where id=?",o.get("id")); } catch(Exception ignored) {} }
  private void outbox(UUID n,String tipo,String clave,Object payload) { try {db.update("insert into agenda.outbox(negocio_id,tipo_evento,clave_particion,payload) values(?,?,?,?::jsonb)",n,tipo,clave,json.writeValueAsString(payload));}catch(Exception e){throw new IllegalStateException(e);} }
  private UUID negocio(String slug){ try{return db.queryForObject("select id from agenda.negocio where slug_publico=?",UUID.class,slug);}catch(Exception e){throw new NoEncontrado("No encontramos ese negocio.");} }
  private Map<String,Object> one(String q,Object...a){ List<Map<String,Object>> r=db.queryForList(q,a);if(r.isEmpty())throw new NoEncontrado("No encontramos el recurso solicitado.");return r.getFirst(); }
  private Map<String,Object> respuesta(Map<String,Object> c,String slug){ String cel=(String)c.get("cliente_celular"); Instant inicio=instante(c.get("inicio")); return Map.of("id",c.get("id"),"inicio",inicio,"fin",instante(c.get("fin")),"horaLocal",inicio.atZone(ZoneId.of("America/Bogota")).format(DateTimeFormatter.ofPattern("HH:mm")),"servicioNombre",c.get("servicio"),"profesionalNombre",c.get("profesional"),"clienteNombre",c.get("cliente_nombre"),"clienteCelular",cel.substring(0,3)+"****"+cel.substring(cel.length()-3),"estado",c.get("estado"),"enlaceGestion","/v1/gestion/"+c.get("token")); }
  private Instant instante(Object valor) { if(valor instanceof Instant i) return i; if(valor instanceof OffsetDateTime o) return o.toInstant(); if(valor instanceof java.sql.Timestamp t) return t.toInstant(); throw new IllegalArgumentException("Instante inválido en la base de datos."); }
  private LocalTime hora(Object valor) { if(valor instanceof LocalTime t) return t; if(valor instanceof java.sql.Time t) return t.toLocalTime(); throw new IllegalArgumentException("Hora inválida en la base de datos."); }
  private String token(){byte[] b=new byte[18];RANDOM.nextBytes(b);return Base64.getUrlEncoder().withoutPadding().encodeToString(b);}
  public record Reserva(@NotNull UUID servicioId,@NotNull UUID profesionalId,@NotNull Instant inicio,@NotBlank @Size(min=2,max=120) String clienteNombre,@NotBlank @Pattern(regexp="^[0-9]{10}$") String clienteCelular){}
  public record EstadoRequest(@NotBlank @Pattern(regexp="ATENDIDA|NO_ASISTIO") String estado){}
  @ResponseStatus(HttpStatus.NOT_FOUND) static class NoEncontrado extends RuntimeException { NoEncontrado(String m){super(m);} }
}
