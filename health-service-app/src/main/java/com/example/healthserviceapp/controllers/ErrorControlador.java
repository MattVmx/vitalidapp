package com.example.healthserviceapp.controllers;

import javax.servlet.RequestDispatcher;
import javax.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

@Controller
public class ErrorControlador implements ErrorController {

    @RequestMapping(value = "/error", method = {RequestMethod.GET, RequestMethod.POST})
    public ModelAndView renderErrorPage(HttpServletRequest httpRequest) {
        ModelAndView errorPage = new ModelAndView("error");
        int httpErrorCode = getErrorCode(httpRequest);
        String errorTitle;
        String errorMsg;

        switch (httpErrorCode) {
            case 400:
                errorTitle = "Solicitud inválida";
                errorMsg = "La solicitud contiene datos inválidos o incompletos.";
                break;
            case 401:
                errorTitle = "Sesión requerida";
                errorMsg = "Necesitás iniciar sesión para continuar.";
                break;
            case 403:
                errorTitle = "Acceso restringido";
                errorMsg = "No tenés permisos para acceder a este recurso.";
                break;
            case 404:
                errorTitle = "Página no encontrada";
                errorMsg = "No encontramos la página solicitada o puede que haya cambiado de ubicación.";
                break;
            case 500:
                errorTitle = "Algo salió mal";
                errorMsg = "Ocurrió un error interno. Intentá nuevamente en unos minutos.";
                break;
            default:
                errorTitle = "No pudimos completar la solicitud";
                errorMsg = "Ocurrió un inconveniente inesperado.";
                break;
        }

        errorPage.addObject("codigo", httpErrorCode);
        errorPage.addObject("titulo", errorTitle);
        errorPage.addObject("mensaje", errorMsg);
        return errorPage;
    }

    private int getErrorCode(HttpServletRequest httpRequest) {
        Object statusCode = httpRequest.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);

        if (statusCode instanceof Integer) {
            return (Integer) statusCode;
        }

        if (statusCode instanceof String) {
            try {
                return Integer.parseInt((String) statusCode);
            } catch (NumberFormatException ignored) {
                // Se usa el estado de respaldo definido debajo.
            }
        }

        return HttpStatus.INTERNAL_SERVER_ERROR.value();
    }

    public String getErrorPath() {
        return "/error";
    }
}
