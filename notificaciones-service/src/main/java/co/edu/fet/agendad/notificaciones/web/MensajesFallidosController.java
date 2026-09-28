package co.edu.fet.agendad.notificaciones.web;

import co.edu.fet.agendad.notificaciones.dominio.MensajeFallidoRepository;
import co.edu.fet.agendad.notificaciones.web.dto.MensajeFallidoResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** docs/TAREAS.md #16: "Endpoint que lista lo caído y alerta en el panel." */
@RestController
public class MensajesFallidosController {

    private final MensajeFallidoRepository mensajeFallidoRepository;

    public MensajesFallidosController(MensajeFallidoRepository mensajeFallidoRepository) {
        this.mensajeFallidoRepository = mensajeFallidoRepository;
    }

    @GetMapping("/v1/mensajes-fallidos")
    public List<MensajeFallidoResponse> listar() {
        return mensajeFallidoRepository.findAllByOrderByFallidoEnDesc().stream()
                .map(m -> new MensajeFallidoResponse(
                        m.getId(), m.getTopicoOrigen(), m.getPayload(), m.getMotivo(),
                        m.getIntentos(), m.getFallidoEn()))
                .toList();
    }
}
